package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitParserTest {
    @Test fun parsesCommonUnits() {
        assertEquals(BaseUnit.GRAM, UnitParser.parse("Net Wt 12 oz")!!.base)
        assertEquals(340.19, UnitParser.parse("12 oz")!!.amount, 0.01)
        assertEquals(1500.0, UnitParser.parse("1.5L")!!.amount, 0.001)
        assertEquals(500.0, UnitParser.parse("500g")!!.amount, 0.001)
        assertEquals(BaseUnit.MILLILITER, UnitParser.parse("16.9 fl oz")!!.base)
        assertEquals(12.0, UnitParser.parse("12 ct")!!.amount, 0.001)
    }

    @Test fun doesNotMatchWordsStartingWithUnit() {
        assertNull(UnitParser.parse("12 large eggs"))
    }

    @Test fun unitPriceLabel() {
        val q = UnitParser.parse("500 g")!!
        assertEquals(true, UnitParser.unitPriceLabel(250, q, imperial = false).endsWith("/ 100 g"))
        val each = UnitParser.parse("4 pack")!!
        assertEquals(true, UnitParser.unitPriceLabel(400, each, imperial = true).endsWith("/ ea"))
        assertEquals(true, UnitParser.unitPriceLabel(340, UnitParser.parse("12 oz")!!, imperial = true).endsWith("/ oz"))
        // A gallon of milk per fl oz would be ~3¢: step up to quarts / liters instead.
        assertEquals(true, UnitParser.unitPriceLabel(338, UnitParser.parse("1 gal")!!, imperial = true).endsWith("/ qt"))
        assertEquals(true, UnitParser.unitPriceLabel(338, UnitParser.parse("1 gal")!!, imperial = false).endsWith("/ L"))
    }
}
