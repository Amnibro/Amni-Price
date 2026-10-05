package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceParserTest {
    private fun line(text: String, height: Int) = OcrLine(text, 0, 0, 100, height)

    @Test fun picksBiggestPriceOverUnitPrice() {
        val result = PriceParser.parse(
            listOf(
                line("Great Value Whole Milk", 30),
                line("1 gal", 15),
                line("$3.48", 90),
                line("$0.03 per fl oz", 12),
            ),
        )
        assertEquals(348L, result.priceCents)
        assertEquals("Great Value Whole Milk", result.productName)
        assertEquals("1 gal", result.sizeText)
    }

    @Test fun ignoresBarcodeStripesReadAsText() {
        val result = PriceParser.parse(listOf(line("LARGE EGGS 12 CT", 30), line("2.99", 120), line("IHMNNVMNNMI", 60)))
        assertEquals("LARGE EGGS 12 CT", result.productName)
    }
    @Test fun readsSuperscriptCentsSplitBySpace() {
        assertEquals(399L, PriceParser.parseText("$3 99").priceCents)
    }

    @Test fun multiBuyIsConvertedToSingleUnitPrice() {
        assertEquals(250L, PriceParser.parseText("2 for $5").priceCents)
        assertEquals(167L, PriceParser.parseText("3/\$5.00").priceCents)
    }

    @Test fun detectsSale() {
        val r = PriceParser.parseText("SALE\nOreo Cookies\n$2.99")
        assertTrue(r.onSale)
        assertEquals(299L, r.priceCents)
        assertEquals("Oreo Cookies", r.productName)
    }

    @Test fun ignoresNoiseLines() {
        val r = PriceParser.parseText("Bananas\nSKU 1234.56\n$0.59")
        assertEquals(59L, r.priceCents)
    }

    @Test fun emptyInput() {
        assertEquals(null, PriceParser.parse(emptyList()).priceCents)
    }
}
