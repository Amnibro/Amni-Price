package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalPricesTest {
    private val columbus = LatLng(39.9612, -82.9988)
    private val dublin = LatLng(40.0992, -83.1141)

    @Test fun distanceAndGeohash() {
        // Columbus → Dublin, OH is roughly 18.7 km.
        assertEquals(18_700.0, Geo.distanceMeters(columbus, dublin), 600.0)
        assertEquals("dqcjqcpe", Geo.geohash(LatLng(38.8977, -77.0365), 8)) // the White House
        assertEquals(Geo.geohash(columbus, 5), Geo.geohash(LatLng(39.9620, -82.9990), 5))
    }

    @Test fun groupsByRegionNameAndComparesAverages() {
        val values = listOf(
            GeoStoreValue(1, columbus, "Columbus", 329.0),
            GeoStoreValue(2, LatLng(39.97, -83.01), "columbus ", 389.0),
            GeoStoreValue(3, dublin, "Dublin", 419.0),
            GeoStoreValue(4, LatLng(40.10, -83.12), "Dublin", 449.0),
        )
        val regions = RegionalPrices.byRegion(values)
        assertEquals(listOf("Columbus", "Dublin"), regions.map { it.label })
        assertEquals(359.0, regions[0].average, 1e-9)
        assertEquals(1L, regions[0].cheapestStoreId)
        assertEquals(0.0, regions[0].premiumPercent, 1e-9)
        assertEquals((434.0 / 359.0 - 1) * 100, regions[1].premiumPercent, 1e-9)
        assertTrue(regions[0].radiusMeters > 0)
    }

    @Test fun unnamedStoresGroupByMapCell() {
        val values = listOf(
            GeoStoreValue(1, columbus, null, 100.0),
            GeoStoreValue(2, LatLng(39.9615, -82.9985), "", 120.0),
            GeoStoreValue(3, dublin, null, 90.0),
        )
        val regions = RegionalPrices.byRegion(values)
        assertEquals(2, regions.size)
        assertTrue(regions.all { it.label.startsWith("Area ") })
        assertEquals(listOf(3L), regions.first().storeIds)
    }

    @Test fun weightedAverageForBasketIndex() {
        val values = listOf(
            GeoStoreValue(1, columbus, "A", 1.00, weight = 9),
            GeoStoreValue(2, columbus, "A", 1.50, weight = 1),
        )
        assertEquals(1.05, RegionalPrices.byRegion(values).single().average, 1e-9)
    }

    @Test fun cheapestNearbyAndNearest() {
        val values = listOf(
            GeoStoreValue(1, columbus, null, 300.0),
            GeoStoreValue(2, dublin, null, 280.0),
        )
        val all = RegionalPrices.cheapestNearby(values, here = columbus)
        assertEquals(listOf(2L, 1L), all.map { it.store.storeId })
        assertEquals(0.0, all[1].distanceMeters!!, 1e-6)
        val within5km = RegionalPrices.cheapestNearby(values, here = columbus, maxDistanceMeters = 5_000.0)
        assertEquals(listOf(1L), within5km.map { it.store.storeId })

        assertEquals(1L, RegionalPrices.nearest(LatLng(39.9613, -82.9989), values, 200.0) { it.position }?.storeId)
        assertNull(RegionalPrices.nearest(LatLng(41.0, -81.0), values, 200.0) { it.position })
    }

    @Test fun bands() {
        assertEquals(RegionalPrices.PriceBand.CHEAPEST, RegionalPrices.band(300.0, 300.0))
        assertEquals(RegionalPrices.PriceBand.NEAR, RegionalPrices.band(320.0, 300.0))
        assertEquals(RegionalPrices.PriceBand.HIGHER, RegionalPrices.band(340.0, 300.0))
    }

    @Test fun formatsDistance() {
        assertEquals("1.2 km", Geo.formatDistance(1234.0, imperial = false))
        assertEquals("850 m", Geo.formatDistance(850.0, imperial = false))
        assertEquals("0.8 mi", Geo.formatDistance(1287.0, imperial = true))
    }
}
