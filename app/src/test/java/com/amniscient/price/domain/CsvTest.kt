package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun roundTrip() {
        val rows = listOf(listOf("a", "b,c", "say \"hi\""), listOf("multi\nline", "", "x"))
        assertEquals(rows, Csv.read(Csv.write(rows)))
    }

    @Test fun moneyParsing() {
        assertEquals(399L, Money.parse("$3.99"))
        assertEquals(399L, Money.parse("3,99"))
        assertEquals(129900L, Money.parse("1,299.00"))
        assertEquals(null, Money.parse("abc"))
    }
}
