package com.amniscient.price.data

import androidx.room.withTransaction
import com.amniscient.price.domain.Csv
import com.amniscient.price.domain.LatestPrice
import com.amniscient.price.domain.Money
import com.amniscient.price.domain.ReceiptItem
import com.amniscient.price.domain.StoreRanker
import com.amniscient.price.domain.StoreScore
import com.amniscient.price.domain.normalizeName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

data class StorePrice(val storeName: String, val latest: PriceEntity)

data class ProductDetail(
    val product: ProductEntity,
    val byStore: List<StorePrice>,
    val history: List<PriceWithStore>,
)

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
)

class PriceRepository(private val db: AppDatabase) {
    private val stores = db.storeDao()
    private val products = db.productDao()
    private val prices = db.priceDao()

    val allStores: Flow<List<StoreEntity>> = stores.observeAll()

    val productSummaries: Flow<List<ProductSummary>> =
        combine(products.observeAll(), prices.observeLatest(), stores.observeAll()) { prods, latest, storeList ->
            val storeNames = storeList.associate { it.id to it.name }
            val byProduct = latest.distinctBy { it.productId to it.storeId }.groupBy { it.productId }
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
        combine(prices.observeLatest(), stores.observeAll()) { latest, storeList ->
            val byId = storeList.associateBy { it.id }
            StoreRanker.rank(
                latest.distinctBy { it.productId to it.storeId }
                    .map { LatestPrice(it.productId, it.storeId, it.priceCents) },
            )
                .mapNotNull { score -> byId[score.storeId]?.let { RankedStore(it, score) } }
        }

    fun productDetail(productId: Long): Flow<ProductDetail?> =
        combine(products.observeById(productId), prices.observeForProduct(productId)) { product, history ->
            product ?: return@combine null
            val byStore = history
                .groupBy { it.price.storeId }
                .map { (_, list) -> list.maxBy { it.price.observedAt } }
                .map { StorePrice(it.storeName, it.price) }
                .sortedBy { it.latest.priceCents }
            ProductDetail(product, byStore, history)
        }

    suspend fun findByBarcode(barcode: String): ProductEntity? = products.findByBarcode(barcode)

    suspend fun summaryForBarcode(barcode: String): ProductSummary? {
        val product = products.findByBarcode(barcode) ?: return null
        return productSummaries.first().firstOrNull { it.product.id == product.id }
    }

    // ---- Stores ----

    suspend fun addStore(name: String, location: String? = null): Long {
        stores.findByName(name.trim())?.let { return it.id }
        return stores.insert(StoreEntity(name = name.trim(), location = location?.trim()?.ifEmpty { null }))
    }

    suspend fun updateStore(store: StoreEntity) = stores.update(store)
    suspend fun deleteStore(store: StoreEntity) = stores.delete(store)

    // ---- Prices ----

    suspend fun recordPrice(input: PriceInput): Long = db.withTransaction {
        val product = findOrCreateProduct(input.productName, input.barcode, input.sizeText, input.brand)
        prices.insert(
            PriceEntity(
                productId = product.id,
                storeId = input.storeId,
                priceCents = input.priceCents,
                observedAt = input.observedAt,
                source = input.source,
                onSale = input.onSale,
                note = input.note,
            ),
        )
    }

    suspend fun recordReceipt(storeId: Long, items: List<ReceiptItem>, observedAt: Long = System.currentTimeMillis()) {
        db.withTransaction {
            items.forEach { item ->
                recordPrice(
                    PriceInput(
                        productName = item.name,
                        storeId = storeId,
                        priceCents = item.unitPriceCents,
                        onSale = item.discounted,
                        source = PriceSource.RECEIPT,
                        observedAt = observedAt,
                    ),
                )
            }
        }
    }

    suspend fun deletePrice(price: PriceEntity) = prices.delete(price)

    suspend fun updateProduct(product: ProductEntity) =
        products.update(product.copy(normalizedName = normalizeName(product.name)))

    suspend fun deleteProduct(product: ProductEntity) = products.delete(product)

    private suspend fun findOrCreateProduct(
        name: String,
        barcode: String?,
        sizeText: String?,
        brand: String?,
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
        )
        return product.copy(id = products.insert(product))
    }

    // ---- CSV import / export (share price lists with friends, back up, seed a community DB) ----

    suspend fun exportCsv(): String {
        val storeById = stores.observeAll().first().associateBy { it.id }
        val productById = products.observeAll().first().associateBy { it.id }
        val rows = mutableListOf(CSV_HEADER)
        prices.getAll().forEach { p ->
            val product = productById[p.productId] ?: return@forEach
            val store = storeById[p.storeId] ?: return@forEach
            rows += listOf(
                product.name,
                product.barcode.orEmpty(),
                product.brand.orEmpty(),
                product.sizeText.orEmpty(),
                store.name,
                store.location.orEmpty(),
                Money.toPlain(p.priceCents),
                Instant.ofEpochMilli(p.observedAt).toString(),
                p.source.name,
                p.onSale.toString(),
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
            val storeId = addStore(storeName, row.col("location"))
            val product = findOrCreateProduct(productName, row.col("barcode"), row.col("size"), row.col("brand"))
            if (prices.countExact(product.id, storeId, observedAt, cents) == 0) {
                prices.insert(
                    PriceEntity(
                        productId = product.id,
                        storeId = storeId,
                        priceCents = cents,
                        observedAt = observedAt,
                        source = PriceSource.IMPORT,
                        onSale = row.col("on_sale").toBoolean(),
                    ),
                )
                imported++
            }
        }
        imported
    }

    companion object {
        val CSV_HEADER = listOf(
            "product", "barcode", "brand", "size", "store", "location",
            "price", "observed_at", "source", "on_sale",
        )
    }
}
