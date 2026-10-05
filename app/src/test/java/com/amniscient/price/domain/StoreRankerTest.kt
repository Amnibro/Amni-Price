package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreRankerTest {
    @Test fun ranksCheapestStoreFirst() {
        val prices = listOf(
            LatestPrice(productId = 1, storeId = 10, priceCents = 100),
            LatestPrice(productId = 1, storeId = 20, priceCents = 120),
            LatestPrice(productId = 2, storeId = 10, priceCents = 300),
            LatestPrice(productId = 2, storeId = 20, priceCents = 330),
            // Only seen at one store: not comparable, ignored.
            LatestPrice(productId = 3, storeId = 20, priceCents = 1),
        )
        val ranking = StoreRanker.rank(prices)
        assertEquals(listOf(10L, 20L), ranking.map { it.storeId })
        assertEquals(1.0, ranking[0].index, 1e-9)
        assertEquals(1.15, ranking[1].index, 1e-9)
        assertEquals(2, ranking[0].cheapestCount)
        assertEquals(0, ranking[1].cheapestCount)
    }
}
