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
    @Test fun readsSuperscriptCentsWithoutDecimalPoint() {
        assertEquals(399L, PriceParser.parse(listOf(line("ORG BABY SPINACH", 30), line("$399", 120), line("UNIT PRICE $0.80 per oz", 14))).priceCents)
        assertEquals(1299L, PriceParser.parse(listOf(line("1299", 120), line("Item 041190", 12))).priceCents)
        assertEquals(249L, PriceParser.parse(listOf(line("2 49", 120))).priceCents)
    }
    @Test fun joinsCentsReadAsTheirOwnLine() {
        val r = PriceParser.parse(listOf(OcrLine("CHOBANI GREEK YOGURT", 0, 0, 400, 30), OcrLine("$1", 200, 60, 260, 180), OcrLine("79", 265, 62, 300, 110), OcrLine("UNIT PRICE 33.6¢ per oz", 0, 200, 300, 214)))
        assertEquals(179L, r.priceCents); assertEquals("CHOBANI GREEK YOGURT", r.productName)
    }
    @Test fun storeChromeIsNotTheProductName() {
        assertEquals("CABOT SHARP CHEDDAR 8 OZ", PriceParser.parse(listOf(line("EVERYDAY LOW PRICE", 40), line("CABOT SHARP CHEDDAR 8 OZ", 30), line("$4.49", 120))).productName)
    }
}
