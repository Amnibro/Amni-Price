package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PriceInsightsTest {
    @Test fun detectsLatestChangePerStore() {
        val obs = listOf(
            Observation(1, 1, 300, 1_000),
            Observation(1, 1, 330, 2_000),
            Observation(1, 2, 310, 1_500), // single observation: no change
            Observation(2, 1, 200, 1_000),
            Observation(2, 1, 200, 3_000), // unchanged
        )
        val changes = PriceInsights.latestChanges(obs)
        assertEquals(1, changes.size)
        assertEquals(10.0, changes[0].percent, 1e-9)
        assertEquals(10.0, PriceInsights.personalInflation(changes)!!, 1e-9)
    }

    @Test fun potentialSavings() {
        val latest = listOf(LatestPrice(1, 1, 300), LatestPrice(1, 2, 350), LatestPrice(2, 1, 100))
        assertEquals(50L, PriceInsights.potentialSavings(latest))
    }

    @Test fun receiptDates() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals(LocalDate.of(2026, 10, 3), ReceiptParser.parseDate("10/03/26 14:22 TR#1234", today))
        assertEquals(LocalDate.of(2026, 9, 28), ReceiptParser.parseDate("Date: 2026-09-28", today))
        assertEquals(LocalDate.of(2026, 9, 25), ReceiptParser.parseDate("25/09/2026", today))
        assertEquals(null, ReceiptParser.parseDate("MILK 3.48", today))
        assertEquals(null, ReceiptParser.parseDate("12/31/2027", today))
    }
}
