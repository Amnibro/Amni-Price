package com.amniscient.price.data

import com.amniscient.price.domain.Categorizer
import com.amniscient.price.domain.LatLng
import java.util.concurrent.TimeUnit

/**
 * Realistic sample data: seven branches across three towns around Columbus, Ohio, a grocery
 * basket and eight weeks of price history, with prices that differ by area. Used by screenshot
 * tests and the debug-only "Load sample data" setting. All prices are made up.
 */
object DemoSeeder {
    private data class Item(val name: String, val size: String?, val barcode: String?, val base: Map<String, Long>)
    private data class DemoStore(val chain: String, val branch: String, val position: LatLng, val region: String, val factor: Double)

    private val stores = listOf(
        DemoStore("ThriftCo", "Oak Ave", LatLng(39.9790, -82.9650), "Columbus", 1.00),
        DemoStore("Hometown Grocers", "Main St", LatLng(39.9560, -82.9900), "Columbus", 1.00),
        DemoStore("Green Basket", "Downtown", LatLng(39.9700, -83.0030), "Columbus", 1.00),
        DemoStore("BigBox Mart", "Riverside", LatLng(40.0990, -83.1140), "Dublin", 1.07),
        DemoStore("Hometown Grocers", "Sawmill", LatLng(40.0880, -83.0900), "Dublin", 1.09),
        DemoStore("ThriftCo", "State St", LatLng(40.1260, -82.9290), "Westerville", 0.96),
        DemoStore("Hometown Grocers", "Polaris", LatLng(40.1450, -82.9800), "Westerville", 0.97),
    )

    private val items = listOf(
        Item("Whole Milk", "1 gal", "240000000137", mapOf("ThriftCo" to 329, "Hometown Grocers" to 389, "BigBox Mart" to 399, "Green Basket" to 549)),
        Item("Large Eggs 12 ct", "12 ct", "240000000274", mapOf("ThriftCo" to 279, "Hometown Grocers" to 349, "BigBox Mart" to 369, "Green Basket" to 499)),
        Item("Bananas", "1 lb", null, mapOf("ThriftCo" to 49, "Hometown Grocers" to 59, "BigBox Mart" to 65, "Green Basket" to 79)),
        Item("Creamy Peanut Butter", "16 oz", "240000000411", mapOf("ThriftCo" to 279, "Hometown Grocers" to 299, "BigBox Mart" to 319)),
        Item("Spaghetti", "16 oz", "240000000548", mapOf("ThriftCo" to 149, "Hometown Grocers" to 179, "BigBox Mart" to 189, "Green Basket" to 229)),
        Item("Chicken Breast", "1 lb", null, mapOf("ThriftCo" to 299, "Hometown Grocers" to 349, "Green Basket" to 699)),
        Item("Laundry Pods 42 ct", "42 ct", "240000000685", mapOf("Hometown Grocers" to 1399, "BigBox Mart" to 1299)),
        Item("Whole Wheat Bread", "20 oz", "240000000822", mapOf("ThriftCo" to 189, "Hometown Grocers" to 249, "BigBox Mart" to 279, "Green Basket" to 449)),
        Item("Cola 12 pack", "12 pack", "240000000959", mapOf("Hometown Grocers" to 749, "BigBox Mart" to 699, "ThriftCo" to 649)),
        Item("Hass Avocados", "4 ct", null, mapOf("ThriftCo" to 349, "Hometown Grocers" to 399, "Green Basket" to 499)),
    )

    suspend fun seed(repository: PriceRepository, now: Long = System.currentTimeMillis()) {
        val storeIds = stores.associateWith { s -> repository.addStore(s.chain, s.branch, s.position, s.region) }
        val day = TimeUnit.DAYS.toMillis(1)
        val inputs = mutableListOf<PriceInput>()
        items.forEachIndexed { idx, item ->
            stores.forEachIndexed { sIdx, store ->
                val base = item.base[store.chain] ?: return@forEachIndexed
                val local = (base * store.factor).toLong()
                // Four visits over eight weeks; prices drift up the way they have been.
                listOf(56, 35, 14, 1).forEachIndexed { visit, daysAgo ->
                    val drift = (local * (visit * (idx % 3 + 1)) / 100.0).toLong()
                    val wobble = if ((idx + visit + sIdx) % 5 == 0) -local / 12 else 0
                    inputs += PriceInput(
                        productName = item.name,
                        storeId = storeIds.getValue(store),
                        priceCents = local + drift + wobble,
                        barcode = item.barcode,
                        sizeText = item.size,
                        onSale = wobble != 0L,
                        source = if (visit % 2 == 0) PriceSource.SHELF else PriceSource.RECEIPT,
                        observedAt = now - daysAgo * day - (idx * 7 + sIdx) * 60_000L,
                        category = Categorizer.categorize(item.name),
                    )
                }
            }
        }
        repository.recordAll(inputs)
        listOf("Whole Milk", "Large Eggs 12 ct", "Bananas", "Laundry Pods 42 ct", "Spaghetti", "Coffee filters")
            .forEach { repository.addToList(it) }
    }
}
