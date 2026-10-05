package com.amniscient.price.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RowGrouperTest {
    @Test fun mergesColumnsIntoRows() {
        val lines = listOf(
            OcrLine("3.48", 400, 100, 450, 120),
            OcrLine("MILK", 10, 102, 80, 122),
            OcrLine("BREAD", 10, 140, 90, 160),
            OcrLine("2.50", 400, 139, 450, 159),
        )
        assertEquals(listOf("MILK  3.48", "BREAD  2.50"), RowGrouper.group(lines))
    }
}
