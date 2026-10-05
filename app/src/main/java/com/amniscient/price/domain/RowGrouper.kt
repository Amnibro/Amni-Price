package com.amniscient.price.domain

/**
 * OCR engines often return the item names and prices of a receipt as separate
 * column blocks. This stitches lines back into visual rows by vertical position.
 */
object RowGrouper {
    fun group(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.centerY }.average().toFloat()
                val rowHeight = row.map { it.height }.average().toFloat()
                val tolerance = 0.5f * minOf(rowHeight, line.height.toFloat()).coerceAtLeast(1f)
                if (kotlin.math.abs(line.centerY - rowCenter) <= tolerance) {
                    row += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }
}
