package com.amniscient.price.domain

/** A store with a known location and a value to compare (a product's price, or a price index). */
data class GeoStoreValue(
    val storeId: Long,
    val position: LatLng,
    /** Town / area name, if known. Stores without one are grouped by ~5 km map cell. */
    val region: String?,
    val value: Double,
    /** How much evidence backs the value (e.g. products compared); used to weight region averages. */
    val weight: Int = 1,
)

data class RegionStat(
    val key: String,
    val label: String,
    val center: LatLng,
    /** Distance from the center to the farthest member store. */
    val radiusMeters: Double,
    val storeIds: List<Long>,
    val min: Double,
    val average: Double,
    val max: Double,
    val cheapestStoreId: Long,
    /** Region average vs. the cheapest region's average, in percent (0 for the cheapest region). */
    val premiumPercent: Double,
)

data class NearbyOption(val store: GeoStoreValue, val distanceMeters: Double?)

/**
 * Regional price comparison: groups stores into areas and compares what a good (or a whole
 * basket, via store price indexes) costs in each one.
 */
object RegionalPrices {
    fun regionKey(region: String?, position: LatLng): Pair<String, String> {
        val name = region?.trim().orEmpty()
        return if (name.isNotEmpty()) {
            "r:" + normalizeName(name) to name
        } else {
            val cell = Geo.geohash(position, 5)
            "g:$cell" to "Area ${cell.uppercase()}"
        }
    }

    fun byRegion(values: List<GeoStoreValue>): List<RegionStat> {
        val groups = values.groupBy { regionKey(it.region, it.position).first }
        val stats = groups.map { (key, members) ->
            val center = Geo.centroid(members.map { it.position })
            val totalWeight = members.sumOf { it.weight.coerceAtLeast(1) }
            val cheapest = members.minBy { it.value }
            RegionStat(
                key = key,
                label = regionKey(members.first().region, members.first().position).second,
                center = center,
                radiusMeters = members.maxOf { Geo.distanceMeters(center, it.position) },
                storeIds = members.map { it.storeId },
                min = cheapest.value,
                average = members.sumOf { it.value * it.weight.coerceAtLeast(1) } / totalWeight,
                max = members.maxOf { it.value },
                cheapestStoreId = cheapest.storeId,
                premiumPercent = 0.0,
            )
        }
        val best = stats.minOfOrNull { it.average } ?: return emptyList()
        return stats
            .map { it.copy(premiumPercent = if (best > 0) (it.average / best - 1) * 100 else 0.0) }
            .sortedBy { it.average }
    }

    /**
     * Stores ordered by value (cheapest first), with distance from [here] when known.
     * With [maxDistanceMeters], stores farther away than that are left out.
     */
    fun cheapestNearby(values: List<GeoStoreValue>, here: LatLng?, maxDistanceMeters: Double? = null): List<NearbyOption> =
        values
            .map { NearbyOption(it, here?.let { h -> Geo.distanceMeters(h, it.position) }) }
            .filter { maxDistanceMeters == null || it.distanceMeters == null || it.distanceMeters <= maxDistanceMeters }
            .sortedWith(compareBy<NearbyOption> { it.store.value }.thenBy { it.distanceMeters ?: Double.MAX_VALUE })

    /** The closest store within [radiusMeters] of [here], e.g. to suggest "you're at Aldi". */
    fun <T> nearest(here: LatLng, items: List<T>, radiusMeters: Double, position: (T) -> LatLng?): T? =
        items
            .mapNotNull { item -> position(item)?.let { item to Geo.distanceMeters(here, it) } }
            .filter { it.second <= radiusMeters }
            .minByOrNull { it.second }
            ?.first

    enum class PriceBand { CHEAPEST, NEAR, HIGHER }

    /** Bins a value relative to the cheapest one: within 1% is cheapest, within 10% is near. */
    fun band(value: Double, cheapest: Double): PriceBand = when {
        cheapest <= 0 || value <= cheapest * 1.01 -> PriceBand.CHEAPEST
        value <= cheapest * 1.10 -> PriceBand.NEAR
        else -> PriceBand.HIGHER
    }
}
