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
        DemoStore("Aldi", "Oak Ave", LatLng(39.9790, -82.9650), "Columbus", 1.00),
        DemoStore("Kroger", "Main St", LatLng(39.9560, -82.9900), "Columbus", 1.00),
        DemoStore("Whole Foods", "Downtown", LatLng(39.9700, -83.0030), "Columbus", 1.00),
        DemoStore("Target", "Riverside", LatLng(40.0990, -83.1140), "Dublin", 1.07),
        DemoStore("Kroger", "Sawmill", LatLng(40.0880, -83.0900), "Dublin", 1.09),
        DemoStore("Aldi", "State St", LatLng(40.1260, -82.9290), "Westerville", 0.96),
        DemoStore("Kroger", "Polaris", LatLng(40.1450, -82.9800), "Westerville", 0.97),
    )

    private val items = listOf(
        Item("Great Value Whole Milk", "1 gal", "078742351865", mapOf("Aldi" to 329, "Kroger" to 389, "Target" to 399, "Whole Foods" to 549)),
        Item("Large Eggs 12 ct", "12 ct", "011110038364", mapOf("Aldi" to 279, "Kroger" to 349, "Target" to 369, "Whole Foods" to 499)),
        Item("Bananas", "1 lb", null, mapOf("Aldi" to 49, "Kroger" to 59, "Target" to 65, "Whole Foods" to 79)),
        Item("Jif Creamy Peanut Butter", "16 oz", "051500255162", mapOf("Aldi" to 279, "Kroger" to 299, "Target" to 319)),
        Item("Barilla Spaghetti", "16 oz", "076808280739", mapOf("Aldi" to 149, "Kroger" to 179, "Target" to 189, "Whole Foods" to 229)),
        Item("Chicken Breast", "1 lb", null, mapOf("Aldi" to 299, "Kroger" to 349, "Whole Foods" to 699)),
        Item("Tide Pods 42 ct", "42 ct", "037000930389", mapOf("Kroger" to 1399, "Target" to 1299)),
        Item("Whole Wheat Bread", "20 oz", "072250037129", mapOf("Aldi" to 189, "Kroger" to 249, "Target" to 279, "Whole Foods" to 449)),
        Item("Coca-Cola 12 pack", "12 pack", "049000028911", mapOf("Kroger" to 749, "Target" to 699, "Aldi" to 649)),
        Item("Haas Avocados", "4 ct", null, mapOf("Aldi" to 349, "Kroger" to 399, "Whole Foods" to 499)),
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
        listOf("Great Value Whole Milk", "Large Eggs 12 ct", "Bananas", "Tide Pods 42 ct", "Barilla Spaghetti", "Coffee filters")
            .forEach { repository.addToList(it) }
    }
}
