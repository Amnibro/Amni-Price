package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    @Test fun categorizes() {
        assertEquals(Category.DAIRY, Categorizer.categorize("Great Value Whole Milk"))
        assertEquals(Category.PANTRY, Categorizer.categorize("Jif Peanut Butter 16 oz"))
        assertEquals(Category.FROZEN, Categorizer.categorize("Ben & Jerry's Ice Cream"))
        assertEquals(Category.BABY_PET, Categorizer.categorize("Purina Dog Chow"))
        assertEquals(Category.PRODUCE, Categorizer.categorize("BANANAS"))
        assertEquals(Category.OTHER, Categorizer.categorize("Gift card"))
        assertEquals(Category.DAIRY, Categorizer.categorize("GV WHL MLK GAL"))
        assertEquals(Category.MEAT, Categorizer.categorize("CHKN BRST 1.8LB"))
        assertEquals(Category.BAKERY, Categorizer.categorize("KRO WHT BREAD"))
        assertEquals(Category.DAIRY, Categorizer.categorize("LG EGGS 12CT"))
    }
}
