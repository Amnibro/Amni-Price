package com.amniscient.price.domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class TagFocusTest {
    private fun l(t: String, x: Int, y: Int, h: Int = 40) = OcrLine(t, x - 60, y - h / 2, x + 60, y + h / 2)
    @Test fun keepsOnlyTheTagUnderTheCenter() {
        val lines = listOf(l("$2.49", 120, 640, 120), l("SPRING WATER", 120, 560), l("$3.99", 540, 640, 120), l("BABY SPINACH 5 OZ", 540, 560), l("$5.29", 960, 640, 120))
        val p = TagFocus.pick(lines, listOf(TagFocus.Code("041190000001", 130f, 700f), TagFocus.Code("041268000002", 560f, 720f)), 1080, 1280)
        assertEquals(listOf("$3.99", "BABY SPINACH 5 OZ"), p.lines.map { it.text }); assertEquals("041268000002", p.barcode)
        assertEquals(399L, PriceParser.parse(p.lines).priceCents); assertEquals("BABY SPINACH 5 OZ", PriceParser.parse(p.lines).productName)
    }
    @Test fun ignoresBarcodesAtTheEdge() { assertNull(TagFocus.pick(emptyList(), listOf(TagFocus.Code("1", 20f, 640f)), 1080, 1280).barcode) }
    @Test fun withoutFrameSizeKeepsEverything() { assertEquals("9", TagFocus.pick(listOf(l("a", 0, 0)), listOf(TagFocus.Code("9", 0f, 0f)), 0, 0).barcode) }
    @Test fun barcodeVariantsCoverUpcAndEan() {
        assertEquals(listOf("041190000001", "0041190000001"), TagFocus.gtins("041190000001"))
        assertEquals(listOf("0041190000001", "041190000001"), TagFocus.gtins("0041190000001"))
        assertEquals("042100005264", TagFocus.upcE("04252614")); assertEquals(listOf("04252614", "042100005264", "0042100005264"), TagFocus.gtins("04252614"))
    }
}
