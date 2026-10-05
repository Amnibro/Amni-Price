package com.amniscient.price.domain

/** Minimal RFC 4180 CSV reader/writer (quoted fields, embedded commas, quotes and newlines). */
object Csv {
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun write(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(",") { escape(it) } } + "\n"

    fun read(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row += field.toString(); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotEmpty() }) rows += row
        }
        return rows
    }
}
