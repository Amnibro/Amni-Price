package com.amniscient.price.domain

data class Observation(val productId: Long, val storeId: Long, val priceCents: Long, val observedAt: Long)

data class PriceChange(
    val productId: Long,
    val storeId: Long,
    val previousCents: Long,
    val currentCents: Long,
    val previousAt: Long,
    val currentAt: Long,
) {
    val percent: Double get() = if (previousCents == 0L) 0.0 else (currentCents - previousCents) * 100.0 / previousCents
    val isIncrease: Boolean get() = currentCents > previousCents
}

object PriceInsights {
    /** For every product at every store: the latest price vs. the one before it, when they differ. */
    fun latestChanges(observations: List<Observation>): List<PriceChange> =
        observations
            .groupBy { it.productId to it.storeId }
            .values
            .mapNotNull { list ->
                val sorted = list.sortedByDescending { it.observedAt }
                val current = sorted.first()
                val previous = sorted.drop(1).firstOrNull { it.observedAt < current.observedAt } ?: return@mapNotNull null
                if (previous.priceCents == current.priceCents) return@mapNotNull null
                PriceChange(
                    current.productId, current.storeId, previous.priceCents, current.priceCents,
                    previous.observedAt, current.observedAt,
                )
            }
            .sortedByDescending { kotlin.math.abs(it.percent) }

    /** Change of the latest price at a store vs. the previous visit, for a live scan. */
    fun changeAgainst(previous: Observation?, currentCents: Long): Double? {
        if (previous == null || previous.priceCents == 0L || previous.priceCents == currentCents) return null
        return (currentCents - previous.priceCents) * 100.0 / previous.priceCents
    }

    /** Average % change across every tracked product/store pair that changed: your personal inflation rate. */
    fun personalInflation(changes: List<PriceChange>): Double? =
        changes.takeIf { it.isNotEmpty() }?.map { it.percent }?.average()

    /** Sum over multi-store products of (highest − lowest latest price): what one of each costs you to shop wrong. */
    fun potentialSavings(latest: List<LatestPrice>): Long =
        latest.groupBy { it.productId }
            .values
            .filter { g -> g.map { it.storeId }.distinct().size >= 2 }
            .sumOf { g -> g.maxOf { it.priceCents } - g.minOf { it.priceCents } }
}
