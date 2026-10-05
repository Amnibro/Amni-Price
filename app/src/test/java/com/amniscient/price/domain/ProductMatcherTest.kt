package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMatcherTest {
    private val catalog = listOf("Great Value Whole Milk", "Whole Wheat Bread", "Chicken Breast", "Bananas", "Large Eggs 12 ct")

    @Test fun matchesReceiptAbbreviations() {
        assertEquals("Great Value Whole Milk", ProductMatcher.best("GV WHL MLK", catalog) { it }?.first)
        assertEquals("Chicken Breast", ProductMatcher.best("CHKN BRST", catalog) { it }?.first)
        assertEquals("Bananas", ProductMatcher.best("BANANA", catalog) { it }?.first)
        assertEquals("Whole Wheat Bread", ProductMatcher.best("WHL WHT BREAD", catalog) { it }?.first)
        assertEquals("Large Eggs 12 ct", ProductMatcher.best("LG EGGS 12CT", catalog) { it }?.first)
    }

    @Test fun rejectsUnrelated() {
        assertNull(ProductMatcher.best("PAPER TOWELS", catalog) { it })
    }

    @Test fun exactIsPerfect() {
        assertEquals(1.0, ProductMatcher.score("Bananas", "bananas"), 1e-9)
        assertTrue(ProductMatcher.score("GV WHL MLK", "Great Value Whole Milk") > ProductMatcher.score("GV WHL MLK", "Whole Wheat Bread"))
    }
}
