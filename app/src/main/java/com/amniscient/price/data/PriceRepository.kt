package com.amniscient.price.data

import androidx.room.withTransaction
import com.amniscient.price.domain.Categorizer
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.Csv
import com.amniscient.price.domain.GeoStoreValue
import com.amniscient.price.domain.LatLng
import com.amniscient.price.domain.LatestPrice
import com.amniscient.price.domain.ChannelObservation
import com.amniscient.price.domain.ChannelSummary
import com.amniscient.price.domain.Markup
import com.amniscient.price.domain.Markups
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.Observation
import com.amniscient.price.domain.PriceChange
import com.amniscient.price.domain.PriceInsights
import com.amniscient.price.domain.ProductMatcher
import com.amniscient.price.domain.RegionStat
import com.amniscient.price.domain.RegionalPrices
import com.amniscient.price.domain.StoreRanker
import com.amniscient.price.domain.StoreScore
import com.amniscient.price.domain.TripItem
import com.amniscient.price.domain.TripPlan
import com.amniscient.price.domain.TripPlanner
import com.amniscient.price.domain.normalizeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

data class ProductSummary(
    val product: ProductEntity,
    val cheapestCents: Long?,
    val cheapestStore: String?,
    val highestCents: Long?,
    val storeCount: Int,
    val lastSeen: Long?,
) {
    /** How much you save buying at the cheapest store vs. the most expensive one. */
    val spreadCents: Long get() = if (cheapestCents != null && highestCents != null) highestCents - cheapestCents else 0
}

data class RankedStore(val store: StoreEntity, val score: StoreScore)

data class StorePrice(val storeId: Long, val storeName: String, val latest: PriceEntity)

data class ProductDetail(
    val product: ProductEntity,
    val byStore: List<StorePrice>,
    val history: List<PriceWithStore>,
    /** Latest change at each store (vs. the previous visit), if any. */
    val changes: Map<Long, PriceChange>,
    val markups: List<Markup> = emptyList(),
)

data class NamedChange(val change: PriceChange, val productName: String, val storeName: String)

data class Insights(
    val productCount: Int,
    val storeCount: Int,
    val priceCount: Int,
    val potentialSavingsCents: Long,
    val inflationPercent: Double?,
    val changes: List<NamedChange>,
    val topStore: RankedStore?,
    val channels: List<ChannelSummary> = emptyList(),
)

data class StoreItem(val product: ProductEntity, val priceCents: Long, val observedAt: Long, val cheapestElsewhere: Long?)

data class StoreDetail(
    val store: StoreEntity,
    val rank: Int?,
    val score: StoreScore?,
    val items: List<StoreItem>,
    val visits: Int,
)

/** One store on the price map. [value] is a price in cents, or a price index in basket mode. */
data class MapPoint(
    val store: StoreEntity,
    val value: Double,
    val priceCents: Long?,
    val observedAt: Long?,
    val weight: Int = 1,
)

data class MapData(
    val located: List<MapPoint>,
    /** Stores with a price but no map position yet. */
    val unlocated: List<MapPoint>,
    val regions: List<RegionStat>,
) {
    val cheapest: Double? get() = located.minOfOrNull { it.value }

    fun geoValues(): List<GeoStoreValue> = located.map {
        GeoStoreValue(it.store.id, it.store.position!!, it.store.region, it.value, it.weight)
    }

    companion object {
        fun of(points: List<MapPoint>): MapData {
            val (located, unlocated) = points.partition { it.store.position != null }
            val data = MapData(located, unlocated, emptyList())
            return data.copy(regions = RegionalPrices.byRegion(data.geoValues()))
        }
    }
}

data class ListEntry(val item: ShoppingItemEntity, val product: ProductEntity?, val cheapestCents: Long?, val cheapestStore: String?)

data class ShoppingPlan(val entries: List<ListEntry>, val plan: TripPlan, val storeNames: Map<Long, String>)

/** Everything needed to record one observed price. */
data class PriceInput(
    val productName: String,
    val storeId: Long,
    val priceCents: Long,
    val barcode: String? = null,
    val sizeText: String? = null,
    val brand: String? = null,
    val onSale: Boolean = false,
    val source: PriceSource = PriceSource.MANUAL,
    val observedAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    /** Attach to this existing product instead of looking one up by barcode/name. */
    val productId: Long? = null,
    /** Category for a newly created product; auto-detected when null. */
    val category: Category? = null,
    val channel: PriceChannel = PriceChannel.IN_STORE,
)

class PriceRepository(private val db: AppDatabase) {
    private val stores = db.storeDao()
    private val products = db.productDao()
    private val prices = db.priceDao()
    private val shopping = db.shoppingDao()

    val allStores: Flow<List<StoreEntity>> = stores.observeAll()
    val allProducts: Flow<List<ProductEntity>> = products.observeAll()

    private val latestDistinct: Flow<List<PriceEntity>> =
        prices.observeLatest().map { list -> list.distinctBy { it.productId to it.storeId } }

    val productSummaries: Flow<List<ProductSummary>> =
        combine(products.observeAll(), latestDistinct, stores.observeAll()) { prods, latest, storeList ->
            val storeNames = storeList.associate { it.id to it.displayName }
            val byProduct = latest.groupBy { it.productId }
            prods.map { product ->
                val entries = byProduct[product.id].orEmpty()
                val cheapest = entries.minByOrNull { it.priceCents }
                ProductSummary(
                    product = product,
                    cheapestCents = cheapest?.priceCents,
                    cheapestStore = cheapest?.let { storeNames[it.storeId] },
                    highestCents = entries.maxOfOrNull { it.priceCents },
                    storeCount = entries.map { it.storeId }.distinct().size,
                    lastSeen = entries.maxOfOrNull { it.observedAt },
                )
            }
        }

    val storeRanking: Flow<List<RankedStore>> =
        combine(latestDistinct, stores.observeAll()) { latest, storeList ->
            rank(latest, storeList)
        }

    val recentPrices: Flow<List<PriceRow>> = prices.observeRecent(12)

    val insights: Flow<Insights> =
        combine(
            products.observeAll(),
            stores.observeAll(),
            prices.observeOwn(),
            latestDistinct,
        ) { prods, storeList, all, latest ->
            val productNames = prods.associate { it.id to it.name }
            val storeNames = storeList.associate { it.id to it.displayName }
            val mine = all.mapTo(HashSet()) { it.productId }
            val tracked = latest.filter { it.productId in mine }
            val changes = PriceInsights.latestChanges(all.filter { it.channel == PriceChannel.IN_STORE }.map { Observation(it.productId, it.storeId, it.priceCents, it.observedAt) })
            Insights(
                productCount = mine.size,
                storeCount = all.mapTo(HashSet()) { it.storeId }.size,
                priceCount = all.size,
                potentialSavingsCents = PriceInsights.potentialSavings(tracked.map { LatestPrice(it.productId, it.storeId, it.priceCents) }),
                inflationPercent = PriceInsights.personalInflation(changes),
                changes = changes.mapNotNull { c ->
                    val p = productNames[c.productId] ?: return@mapNotNull null
                    val s = storeNames[c.storeId] ?: return@mapNotNull null
                    NamedChange(c, p, s)
                },
                topStore = rank(tracked, storeList).firstOrNull(),
                channels = Markups.byChannel(Markups.latest(all.map { ChannelObservation(it.productId, it.storeId, it.channel.name, it.priceCents, it.observedAt) })),
            )
        }

    private fun rank(latest: List<PriceEntity>, storeList: List<StoreEntity>): List<RankedStore> {
        val byId = storeList.associateBy { it.id }
        return StoreRanker.rank(latest.map { LatestPrice(it.productId, it.storeId, it.priceCents) })
            .mapNotNull { score -> byId[score.storeId]?.let { RankedStore(it, score) } }
    }

    fun productDetail(productId: Long): Flow<ProductDetail?> =
        combine(products.observeById(productId), prices.observeForProduct(productId)) { product, history ->
            product ?: return@combine null
            val inStore = history.filter { it.price.channel == PriceChannel.IN_STORE }
            val byStore = inStore
                .groupBy { it.price.storeId }
                .map { (_, list) -> list.maxBy { it.price.observedAt } }
                .map { StorePrice(it.price.storeId, it.storeName, it.price) }
                .sortedBy { it.latest.priceCents }
            val changes = PriceInsights
                .latestChanges(inStore.map { Observation(it.price.productId, it.price.storeId, it.price.priceCents, it.price.observedAt) })
                .associateBy { it.storeId }
            ProductDetail(product, byStore, history, changes, Markups.latest(history.map { ChannelObservation(it.price.productId, it.price.storeId, it.price.channel.name, it.price.priceCents, it.price.observedAt) }))
        }

    /** Everything the price map needs for one product: located stores, their latest prices, regions. */
    fun productMap(productId: Long): Flow<MapData> =
        combine(latestDistinct, stores.observeAll()) { latest, storeList ->
            val byId = storeList.associateBy { it.id }
            val points = latest.filter { it.productId == productId }.mapNotNull { p ->
                val store = byId[p.storeId] ?: return@mapNotNull null
                MapPoint(store, p.priceCents.toDouble(), p.priceCents, p.observedAt)
            }
            MapData.of(points)
        }

    /** The whole-basket view: every located store's price index (1.00 = always cheapest). */
    val basketMap: Flow<MapData> =
        combine(latestDistinct, stores.observeAll()) { latest, storeList ->
            MapData.of(rank(latest, storeList).map { r -> MapPoint(r.store, r.score.index, null, null, r.score.comparedProducts) })
        }

    fun storeDetail(storeId: Long): Flow<StoreDetail?> =
        combine(stores.observeById(storeId), latestDistinct, products.observeAll(), storeRanking, prices.observeAll()) {
                store, latest, prods, ranking, all ->
            store ?: return@combine null
            val productById = prods.associateBy { it.id }
            val byProduct = latest.groupBy { it.productId }
            val items = latest.filter { it.storeId == storeId }.mapNotNull { p ->
                val product = productById[p.productId] ?: return@mapNotNull null
                val elsewhere = byProduct[p.productId].orEmpty().filter { it.storeId != storeId }.minOfOrNull { it.priceCents }
                StoreItem(product, p.priceCents, p.observedAt, elsewhere)
            }.sortedBy { it.product.name.lowercase() }
            val rankIndex = ranking.indexOfFirst { it.store.id == storeId }
            val visits = all.filter { it.storeId == storeId }
                .map { java.time.Instant.ofEpochMilli(it.observedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                .distinct().size
            StoreDetail(
                store = store,
                rank = rankIndex.takeIf { it >= 0 }?.plus(1),
                score = ranking.getOrNull(rankIndex)?.score,
                items = items,
                visits = visits,
            )
        }

    suspend fun findByBarcode(barcode: String): ProductEntity? = products.findByBarcode(barcode)

    suspend fun summaryForBarcode(barcode: String): ProductSummary? {
        val product = products.findByBarcode(barcode) ?: return null
        return productSummaries.first().firstOrNull { it.product.id == product.id }
    }

    /** Best fuzzy match among saved products, for receipt lines and typed shopping-list items. */
    suspend fun matchListItem(name: String): ProductEntity? = withContext(Dispatchers.Default) {
        val own = prices.ownProductIds().toHashSet()
        products.getAll().filter { it.id in own }.map { it to ProductMatcher.covers(name, it.name) }.filter { it.second >= 0.75 }
            .maxWithOrNull(compareBy<Pair<ProductEntity, Double>> { it.second }.thenBy { ProductMatcher.score(name, it.first.name) })?.first
    }
    suspend fun matchProduct(name: String, threshold: Double = ProductMatcher.DEFAULT_THRESHOLD): Pair<ProductEntity, Double>? =
        withContext(Dispatchers.Default) {
            val all = products.getAll()
            all.firstOrNull { it.normalizedName == normalizeName(name) }?.let { return@withContext it to 1.0 }
            ProductMatcher.best(name, all, threshold) { it.name }
        }

    suspend fun lastPriceAt(productId: Long, storeId: Long): PriceEntity? = prices.latestAt(productId, storeId)

    // ---- Stores ----

    /**
     * Adds a store, or returns the existing one with the same name *and* branch label, so
     * "Kroger · Main St" and "Kroger · Westerville" stay separate stores.
     */
    suspend fun addStore(
        name: String,
        location: String? = null,
        position: LatLng? = null,
        region: String? = null,
    ): Long {
        val branch = location?.trim()?.ifEmpty { null }
        stores.findAllByName(name.trim())
            .firstOrNull { it.location.orEmpty().equals(branch.orEmpty(), ignoreCase = true) }
            ?.let { existing ->
                if (position != null && existing.position == null) setStoreLocation(existing.id, position, region)
                return existing.id
            }
        return stores.insert(
            StoreEntity(
                name = name.trim(),
                location = branch,
                latitude = position?.lat,
                longitude = position?.lng,
                region = region?.trim()?.ifEmpty { null },
            ),
        )
    }

    suspend fun storesNamed(name: String): List<StoreEntity> = stores.findAllByName(name.trim())

    suspend fun setStoreLocation(storeId: Long, position: LatLng?, region: String?) {
        val store = stores.getById(storeId) ?: return
        stores.update(
            store.copy(
                latitude = position?.lat,
                longitude = position?.lng,
                region = region?.trim()?.ifEmpty { null } ?: store.region,
            ),
        )
    }

    suspend fun updateStore(store: StoreEntity) = stores.update(store)
    suspend fun deleteStore(store: StoreEntity) = stores.delete(store)

    // ---- Prices ----

    suspend fun recordPrice(input: PriceInput): Long = db.withTransaction {
        val product = input.productId?.let { products.getById(it) }
            ?: findOrCreateProduct(input.productName, input.barcode, input.sizeText, input.brand, input.category ?: Category.MEALS.takeIf { input.source == PriceSource.MENU })
        if (input.source == PriceSource.MENU) stores.getById(input.storeId)?.takeIf { it.kind != StoreKind.RESTAURANT }?.let { stores.update(it.copy(kind = StoreKind.RESTAURANT)) }
        prices.insert(
            PriceEntity(
                productId = product.id,
                storeId = input.storeId,
                priceCents = input.priceCents,
                observedAt = input.observedAt,
                source = input.source,
                onSale = input.onSale,
                note = input.note,
                channel = input.channel,
            ),
        )
    }

    /** Saves several observations atomically (e.g. every line of a receipt). */
    suspend fun recordAll(inputs: List<PriceInput>) = db.withTransaction { inputs.forEach { recordPrice(it) } }

    /** True if this exact price was saved for this product at this store in the last [windowMs]. */
    suspend fun isRecentDuplicate(productName: String, barcode: String?, storeId: Long, priceCents: Long, windowMs: Long = 15 * 60_000): Boolean {
        val product = barcode?.takeIf { it.isNotBlank() }?.let { products.findByBarcode(it) }
            ?: products.findByNormalizedName(normalizeName(productName))
            ?: return false
        val last = prices.latestAt(product.id, storeId) ?: return false
        return last.priceCents == priceCents && System.currentTimeMillis() - last.observedAt < windowMs
    }

    suspend fun deletePrice(price: PriceEntity) = prices.delete(price)

    /** Undo for [deletePrice]: re-inserts the row with its original id. */
    suspend fun restorePrice(price: PriceEntity) {
        prices.insert(price)
    }

    suspend fun updateProduct(product: ProductEntity) =
        products.update(product.copy(normalizedName = normalizeName(product.name)))

    suspend fun deleteProduct(product: ProductEntity) = products.delete(product)

    suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private suspend fun findOrCreateProduct(
        name: String,
        barcode: String?,
        sizeText: String?,
        brand: String?,
        category: Category? = null,
    ): ProductEntity {
        val code = barcode?.trim()?.ifEmpty { null }
        val normalized = normalizeName(name)
        val existing = code?.let { products.findByBarcode(it) }
            ?: products.findByNormalizedName(normalized)?.takeIf { it.barcode == null || code == null || it.barcode == code }
        if (existing != null) {
            // Fill in details we learned from this scan.
            val enriched = existing.copy(
                barcode = existing.barcode ?: code,
                sizeText = existing.sizeText ?: sizeText?.ifBlank { null },
                brand = existing.brand ?: brand?.ifBlank { null },
            )
            if (enriched != existing) products.update(enriched)
            return enriched
        }
        val product = ProductEntity(
            name = name.trim(),
            normalizedName = normalized,
            barcode = code,
            sizeText = sizeText?.ifBlank { null },
            brand = brand?.ifBlank { null },
            category = category ?: Categorizer.categorize(name),
        )
        return product.copy(id = products.insert(product))
    }

    // ---- Shopping list ----

    val shoppingPlan: Flow<ShoppingPlan> =
        combine(shopping.observeAll(), products.observeAll(), latestDistinct, stores.observeAll()) { items, prods, latest, storeList ->
            val productById = prods.associateBy { it.id }
            val storeNames = storeList.associate { it.id to it.displayName }
            val priceMap: Map<Long, Map<Long, Long>> = latest.groupBy { it.productId }
                .mapValues { (_, list) -> list.associate { it.storeId to it.priceCents } }
            val entries = items.map { item ->
                val product = item.productId?.let(productById::get)
                val cheapest = product?.let { p -> latest.filter { it.productId == p.id }.minByOrNull { it.priceCents } }
                ListEntry(item, product, cheapest?.priceCents, cheapest?.let { storeNames[it.storeId] })
            }
            val open = items.filterNot { it.checked }.map { TripItem(it.id, it.productId, it.quantity) }
            ShoppingPlan(entries, TripPlanner.plan(open, priceMap), storeNames)
        }

    /** Adds a typed item, linking it to a saved product when the name matches one. */
    suspend fun addToList(name: String, productId: Long? = null, quantity: Int = 1) {
        val linked = productId ?: matchListItem(name)?.id
        if (linked != null) {
            shopping.findOpenForProduct(linked)?.let { existing ->
                shopping.update(existing.copy(quantity = existing.quantity + quantity))
                return
            }
        }
        val display = linked?.let { products.getById(it)?.name } ?: name.trim()
        shopping.insert(ShoppingItemEntity(name = display, productId = linked, quantity = quantity))
    }

    suspend fun updateListItem(item: ShoppingItemEntity) = shopping.update(item)
    suspend fun deleteListItem(item: ShoppingItemEntity) = shopping.delete(item)
    suspend fun restoreListItem(item: ShoppingItemEntity) { shopping.insert(item) }
    suspend fun clearCheckedItems() = shopping.clearChecked()

    // ---- CSV import / export (share price lists with friends, back up, seed a community DB) ----

    suspend fun exportCsv(): String {
        val storeById = stores.observeAll().first().associateBy { it.id }
        val productById = products.getAll().associateBy { it.id }
        val rows = mutableListOf(CSV_HEADER)
        prices.getAll().filter { it.source != PriceSource.COMMUNITY }.forEach { p ->
            val product = productById[p.productId] ?: return@forEach
            val store = storeById[p.storeId] ?: return@forEach
            rows += listOf(
                product.name,
                product.barcode.orEmpty(),
                product.brand.orEmpty(),
                product.sizeText.orEmpty(),
                product.category.name,
                store.name,
                store.location.orEmpty(),
                store.latitude?.toString().orEmpty(),
                store.longitude?.toString().orEmpty(),
                store.region.orEmpty(),
                Money.toPlain(p.priceCents),
                Instant.ofEpochMilli(p.observedAt).toString(),
                p.source.name,
                p.onSale.toString(),
                p.channel.name,
            )
        }
        return Csv.write(rows)
    }

    /** Returns the number of new price observations imported. Duplicate rows are skipped. */
    suspend fun importCsv(text: String): Int = db.withTransaction {
        val rows = Csv.read(text)
        if (rows.isEmpty()) return@withTransaction 0
        val header = rows.first().map { it.trim().lowercase() }
        fun List<String>.col(name: String): String? =
            header.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it)?.trim() }?.ifEmpty { null }

        var imported = 0
        rows.drop(1).forEach { row ->
            val productName = row.col("product") ?: return@forEach
            val storeName = row.col("store") ?: return@forEach
            val cents = row.col("price")?.let(Money::parse) ?: return@forEach
            val observedAt = row.col("observed_at")
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
            val category = row.col("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() }
            val lat = row.col("latitude")?.toDoubleOrNull()
            val lng = row.col("longitude")?.toDoubleOrNull()
            val position = if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) LatLng(lat, lng) else null
            val storeId = addStore(storeName, row.col("location"), position, row.col("region"))
            val product = findOrCreateProduct(productName, row.col("barcode"), row.col("size"), row.col("brand"), category)
            if (prices.countExact(product.id, storeId, observedAt, cents) == 0) {
                prices.insert(
                    PriceEntity(
                        productId = product.id,
                        storeId = storeId,
                        priceCents = cents,
                        observedAt = observedAt,
                        source = PriceSource.IMPORT,
                        onSale = row.col("on_sale").toBoolean(),
                        channel = row.col("channel")?.let { c -> PriceChannel.entries.firstOrNull { it.name == c } } ?: PriceChannel.IN_STORE,
                    ),
                )
                imported++
            }
        }
        imported
    }

    val communityPriceCount: Flow<Int> = prices.observeCommunityCount()
    suspend fun importCommunity(pack: CommunityPack): Int = db.withTransaction {
        prices.deleteCommunity()
        val existing = stores.community().associateBy { it.externalId }
        val storeIds = pack.stores.map { s ->
            existing[s.externalId]?.let { old -> old.copy(name = s.name, location = s.city.ifBlank { null }, latitude = s.position.lat, longitude = s.position.lng, region = s.city.ifBlank { old.region }).also { if (it != old) stores.update(it) }.id }
                ?: stores.insert(StoreEntity(name = s.name, location = s.city.ifBlank { null }, latitude = s.position.lat, longitude = s.position.lng, region = s.city.ifBlank { null }, externalId = s.externalId))
        }
        val productIds = pack.products.map { p ->
            products.findByBarcode(p.barcode)?.id ?: p.name.ifBlank { "Item ${p.barcode}" }.let { n -> products.insert(ProductEntity(name = n, normalizedName = normalizeName(n), barcode = p.barcode, sizeText = p.quantity, brand = p.brand, category = Categorizer.categorize(n))) }
        }
        val rows = pack.prices.map { PriceEntity(productId = productIds[it.product], storeId = storeIds[it.store], priceCents = it.cents, observedAt = it.observedAt, source = PriceSource.COMMUNITY, onSale = it.onSale) }
        rows.chunked(2000).forEach { prices.insertAll(it) }
        stores.deleteUnusedCommunity()
        products.deleteUnpriced()
        rows.size
    }
    suspend fun removeCommunity() = db.withTransaction {
        prices.deleteCommunity()
        stores.deleteUnusedCommunity()
        products.deleteUnpriced()
    }
    companion object {
        val CSV_HEADER = listOf(
            "product", "barcode", "brand", "size", "category", "store", "location", "latitude", "longitude", "region",
            "price", "observed_at", "source", "on_sale", "channel",
        )
    }
}
