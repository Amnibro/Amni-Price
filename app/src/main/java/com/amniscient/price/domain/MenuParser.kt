package com.amniscient.price.domain
object MenuParser {
    private val calories = Regex("""[•·|]?\s*\d[\d,]*\s*(?:-\s*\d[\d,]*\s*)?(?:k?cal|calories)\.?\b""", RegexOption.IGNORE_CASE)
    private val trailingPrice = Regex("""(?<![#\d.,])\$?\s*(\d{1,3}(?:[.,]\d{2})?)\s*\+?\s*$""")
    private val decimalPrice = Regex("""\d[.,]\d{2}\s*\+?\s*$""")
    private val leaders = Regex("""[\s.·•…_\-–—|]+$""")
    private val skip = Regex("""\b(sub\s*-?total|total|tax|delivery\s+fee|service\s+fee|fees|tip|promo|discount|rating|reviews?|miles?|min(?:utes)?|eta|checkout|add\s+to\s+cart|deliver(?:y|ed)\s+by|pickup)\b""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""^\d{1,2}:\d{2}""")
    fun parse(rows: List<String>): ReceiptResult {
        val lines = rows.map { calories.replace(it, " ").replace(Regex("""[^\p{L}\p{N}$)]+$"""), "").replace(Regex("""^[^\p{L}\p{N}$(]+"""), "").replace(Regex("""\s{2,}"""), " ").trim() }.filter { it.isNotEmpty() }
        val decimalStyle = lines.any { decimalPrice.containsMatchIn(it) }
        val items = mutableListOf<ReceiptItem>()
        var lastUsed = -1
        lines.forEachIndexed { i, line ->
            if (skip.containsMatchIn(line) || clock.containsMatchIn(line)) return@forEachIndexed
            val m = trailingPrice.find(line) ?: return@forEachIndexed
            val raw = m.groupValues[1]
            if (!decimalStyle && raw.any { it == '.' || it == ',' }.not() && !line.contains('$') && raw.toInt() !in 3..199) return@forEachIndexed
            if (decimalStyle && raw.none { it == '.' || it == ',' } && !line.contains('$')) return@forEachIndexed
            val cents = Money.parse(raw)?.takeIf { it in 50..50_000 } ?: return@forEachIndexed
            val name = line.substring(0, m.range.first).replace(Regex("""\s*[.·…_]{3,}.*$"""), "").replace(leaders, "").replace(trailingPrice, "").replace(leaders, "").trim()
            val title = if (name.count(Char::isLetter) >= 3) name else (i - 1 downTo maxOf(lastUsed + 1, i - 4)).map { lines[it] }.firstOrNull(::isTitle)
            if (title != null) { items += ReceiptItem(ReceiptParser.cleanName(title), cents); lastUsed = i }
        }
        return ReceiptResult(ReceiptParser.guessStore(lines.filterNot { trailingPrice.containsMatchIn(it) || clock.containsMatchIn(it) }), items.distinctBy { normalizeName(it.name) to it.priceCents }, null)
    }
    private fun isTitle(s: String) = s.count(Char::isLetter) >= 3 && s.length <= 45 && !s.endsWith('.') && ',' !in s && !s.first().isLowerCase() && s.split(' ').size <= 7 && !trailingPrice.containsMatchIn(s) && !skip.containsMatchIn(s)
}
