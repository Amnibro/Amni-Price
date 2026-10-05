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
}
