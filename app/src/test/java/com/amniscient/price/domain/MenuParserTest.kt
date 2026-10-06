package com.amniscient.price.domain
import org.junit.Assert.assertEquals
import org.junit.Test
class MenuParserTest {
    private fun items(vararg rows: String) = MenuParser.parse(rows.toList()).items.map { it.name to it.priceCents }
    @Test fun printedMenuWithLeaders() {
        assertEquals(listOf("Burrito Bowl" to 1095L, "Chips & Guacamole" to 485L, "Fountain Drink" to 275L),
            items("TACO TOWN", "ENTREES", "Burrito Bowl ........ 10.95", "Chips & Guacamole   \$4.85", "Fountain Drink — 2.75"))
    }
    @Test fun deliveryAppLayoutTitleDescriptionPrice() {
        val r = MenuParser.parse(listOf("9:41", "Chipotle Mexican Grill", "Most Ordered", "Burrito Bowl", "Your choice of freshly grilled meat, rice and beans.", "\$13.70 • 1,050 Cal", "Chicken Burrito", "\$13.70", "Delivery fee \$2.99", "Service fee \$3.10"))
        assertEquals(listOf("Burrito Bowl" to 1370L, "Chicken Burrito" to 1370L), r.items.map { it.name to it.priceCents })
        assertEquals("Chipotle Mexican Grill", r.storeName)
    }
    @Test fun wholeDollarMenus() {
        assertEquals(listOf("Pad Thai" to 1400L, "Green Curry" to 1500L), items("THAI HOUSE", "Pad Thai 14", "Green Curry 15"))
    }
    @Test fun ignoresCountsAndCaloriesInDecimalMenus() {
        assertEquals(listOf("Nuggets 10 pc" to 549L), items("Nuggets 10 pc 5.49", "Combo #1", "Big Mac 590 Cal"))
    }
    @Test fun separatorsMisreadByOcr() {
        assertEquals(listOf("Burrito Bowl" to 1370L, "Chips & Guacamole" to 605L), items("Burrito Bowl", "Your choice of grilled meat, rice and beans.", "\$13.70 * 1,050 Cal", "Chips & Guacamole", "\$6.05 © 770 Cal"))
    }
    @Test fun strayOcrSymbolsAtLineEdges() {
        assertEquals(listOf("Chips & Queso" to 425L, "Chicken Burrito" to 1095L, "Burrito Bowl" to 1370L), items("| Chips & Queso ...........0... 4.25 .", "| Chicken Burrito ............ 10.95", "Burrito Bowl", "\$13.70 « 1,050 Cal"))
    }
}
