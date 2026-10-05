package com.amniscient.price.domain

data class LatestPrice(val productId: Long, val storeId: Long, val priceCents: Long)

/**
 * @property index average of (store price / cheapest price) across products the store
 * shares with at least one other store. 1.00 means always the cheapest; 1.15 means
 * on average 15% more than the cheapest store.
 */
data class StoreScore(
    val storeId: Long,
    val index: Double,
    val comparedProducts: Int,
    val cheapestCount: Int,
)

object StoreRanker {
    fun rank(prices: List<LatestPrice>): List<StoreScore> {
        val ratios = mutableMapOf<Long, MutableList<Double>>()
        val wins = mutableMapOf<Long, Int>()
        prices.groupBy { it.productId }
            .values
            .filter { group -> group.map { it.storeId }.distinct().size >= 2 }
            .forEach { group ->
                val min = group.minOf { it.priceCents }.coerceAtLeast(1)
                group.forEach { p ->
                    ratios.getOrPut(p.storeId) { mutableListOf() } += p.priceCents.toDouble() / min
                    if (p.priceCents <= min) wins[p.storeId] = (wins[p.storeId] ?: 0) + 1
                }
            }
        return ratios.map { (storeId, r) ->
            StoreScore(storeId, r.average(), r.size, wins[storeId] ?: 0)
        }.sortedWith(compareBy<StoreScore> { it.index }.thenByDescending { it.comparedProducts })
    }
}
