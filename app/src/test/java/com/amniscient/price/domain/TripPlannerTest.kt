package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripPlannerTest {
    // Stores: 1 = cheap groceries, 2 = cheap household, 3 = expensive everything
    private val prices = mapOf(
        10L to mapOf(1L to 300L, 2L to 400L, 3L to 450L), // milk
        11L to mapOf(1L to 250L, 2L to 260L, 3L to 300L), // bread
        12L to mapOf(2L to 900L, 3L to 1200L),            // detergent (not at store 1)
    )

    @Test fun findsBestSplit() {
        val items = listOf(TripItem(1, 10), TripItem(2, 11), TripItem(3, 12, quantity = 2), TripItem(4, null))
        val plan = TripPlanner.plan(items, prices)

        assertEquals(listOf(4L), plan.unpriced)
        assertEquals(3, plan.pricedCount)
        // Best single store covering everything: store 2 (400 + 260 + 1800 = 2460).
        assertEquals(2L, plan.singleStore.first().storeId)
        assertEquals(2460L, plan.singleStore.first().totalCents)
        // Split: milk + bread at 1, detergent at 2 = 300 + 250 + 1800 = 2350.
        val best = plan.best!!
        assertEquals(setOf(1L, 2L), best.storeIds.toSet())
        assertEquals(2350L, best.totalCents)
        // vs highest: (450-300) + (300-250) + (1200-900)*2 = 800
        assertEquals(800L, plan.savingsVsHighest)
    }

    @Test fun singleStoreWhenSplitDoesNotHelp() {
        val plan = TripPlanner.plan(listOf(TripItem(1, 10), TripItem(2, 11)), prices)
        assertEquals(listOf(1L), plan.best!!.storeIds)
    }

    @Test fun emptyList() {
        val plan = TripPlanner.plan(emptyList<TripItem>(), prices)
        assertEquals(null, plan.best)
        assertEquals(0L, plan.savingsVsHighest)
    }
}
