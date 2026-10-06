package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    @Test fun parsesTypicalReceipt() {
        val rows = listOf(
            "FRESH MART",
            "123 Main St",
            "GV WHOLE MILK 007874235187 F  3.48 N",
            "BANANAS  0.62",
            "2 @ 1.99",
            "OREO COOKIES  3.98",
            "COUPON  1.00-",
            "SUBTOTAL  8.08",
            "TAX  0.40",
            "TOTAL  8.48",
            "VISA  8.48",
        )
        val r = ReceiptParser.parse(rows)
        assertEquals("FRESH MART", r.storeName)
        assertEquals(848L, r.totalCents)
        assertEquals(3, r.items.size)

        assertEquals("GV WHOLE MILK", r.items[0].name)
        assertEquals(348L, r.items[0].priceCents)

        val oreo = r.items[2]
        assertEquals(2, oreo.quantity)
        assertEquals(298L, oreo.priceCents)
        assertTrue(oreo.discounted)
        assertEquals(149L, oreo.unitPriceCents)
    }

    @Test fun quantityOnSameLine() {
        val r = ReceiptParser.parse(listOf("SHOP", "YOGURT 3 @ 1.00  3.00"))
        assertEquals(1, r.items.size)
        assertEquals("YOGURT", r.items[0].name)
        assertEquals(3, r.items[0].quantity)
        assertEquals(100L, r.items[0].unitPriceCents)
    }
    @Test fun quantityLineSurvivesOcrMisreadingTheAtSign() {
        listOf("2 @ 1.25", "2 @ 1. 25", "2 e 1.25", "2 © 1.25", "2°@ 1.25", "2 x 1,25", "2@1.25").forEach { q ->
            val r = ReceiptParser.parse(listOf("SHOP", "CEREAL 18OZ  4.99", "COUPON  -1.00", q, "GREEK YOGURT  2.50", "TOTAL  6.49"))
            val yogurt = r.items.single { it.name == "GREEK YOGURT" }
            assertEquals(q, 2, yogurt.quantity)
            assertEquals(q, 125L, yogurt.unitPriceCents)
            assertEquals(q, 2, r.items.size)
        }
    }
    @Test fun wordsAreNotMistakenForQuantities() {
        val r = ReceiptParser.parse(listOf("SHOP", "TEA 2 PACK  3.00", "EGGS 12CT  2.99"))
        assertEquals(listOf(1, 1), r.items.map { it.quantity })
    }
    @Test fun joinsDecimalsSplitByOcr() {
        val r = ReceiptParser.parse(listOf("SHOP", "CHKN BRST 1. 8LB  8. 99"))
        assertEquals("CHKN BRST 1.8LB", r.items.single().name)
        assertEquals(899L, r.items.single().priceCents)
    }
}
