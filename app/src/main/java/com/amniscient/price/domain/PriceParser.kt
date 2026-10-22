package com.amniscient.price.domain

data class PriceCandidate(
    val cents: Long,
    val score: Float,
    val isUnitPrice: Boolean,
    val line: String,
)

/** What we could read off a shelf tag / price label. */
data class ShelfTagResult(
    val priceCents: Long?,
    val productName: String?,
    val sizeText: String?,
    val onSale: Boolean,
    val candidates: List<PriceCandidate> = emptyList(),
)

/**
 * Heuristics for shelf tags: the main price is usually the biggest text on the tag,
 * unit prices ("$0.21 / oz") are small and labelled, and the product name is the
 * largest mostly-alphabetic line.
 */
object PriceParser {
    private val opts = setOf(RegexOption.IGNORE_CASE)
    private val amount = """(\d{1,4}(?:[.,]\d{2})?)"""
    private val multiBuyFor = Regex("""(?<!\d)(\d{1,2})\s*for\s*\$?\s*$amount(?!\d)""", opts)
    private val multiBuySlash = Regex("""(?<!\d)(\d{1,2})\s*/\s*\$\s*$amount(?!\d)""", opts)
    private val decimal = Regex("""(?<![\d.,])\$?\s*(\d{1,4})\s?[.,]\s?(\d{2})(?![\d%])""")
    private val dollarSpaceCents = Regex("""\$\s*(\d{1,3})\s+(\d{2})(?!\d)""")
    private val dollarWhole = Regex("""\$\s*(\d{1,4})(?![\d.,])""")
    private val bareSpaced = Regex("""^\s*(\d{1,2})\s+(\d{2})\s*$""")
    private val bareRun = Regex("""^\s*(\d{3,4})\s*$""")
    private val splitDollars = Regex("""^\s*\$?\s*(\d{1,2})\s*$""")
    private val splitCents = Regex("""^\s*(\d{2})\s*[¢c]?\s*$""", opts)
    private val nameNoise = Regex("""\b(price|everyday|rewards?|you pay|with card|limit|guiding stars?|each|ea|per|retail|aisle|shelf)\b""", opts)
    private val cents = Regex("""(?<![\d.])(\d{1,2})\s*[¢c](?![a-z])""", opts)
    private val unitPriceHint = Regex(
        """(\bper\b|/\s*(oz|lb|kg|g|ml|l|ct|each|ea|100\s?g|100\s?ml|fl\s?oz|qt|gal)\b|unit\s*price)""",
        opts,
    )
    private val saleHint = Regex("""\b(sale|save|savings|special|deal|clearance|rollback|bogo|promo|\d+%\s*off)\b""", opts)
    private val noiseHint = Regex("""\b(sku|upc|plu|item\s*#|reg(ular)?\s*price|was)\b""", opts)
    private val barcodeStripes = Regex("""^[IlL1|!iNMHVWUJ\[\]]{6,}$""")

    fun parse(raw: List<OcrLine>): ShelfTagResult {
        if (raw.isEmpty()) return ShelfTagResult(null, null, null, false)
        val lines = joinSuperscript(raw)
        val maxHeight = lines.maxOf { it.height }.coerceAtLeast(1).toFloat()
        val candidates = lines.flatMap { line ->
            val relHeight = if (line.height > 0) line.height / maxHeight else 0.5f
            candidatesIn(line.text).map { (c, bonus) ->
                val unit = unitPriceHint.containsMatchIn(line.text)
                val noise = noiseHint.containsMatchIn(line.text)
                PriceCandidate(
                    cents = c,
                    score = relHeight + bonus - (if (unit) 1f else 0f) - (if (noise) 0.6f else 0f),
                    isUnitPrice = unit,
                    line = line.text,
                )
            }
        }.filter { it.cents in 1..999_999 }

        val best = candidates.filterNot { it.isUnitPrice }.maxByOrNull { it.score }
        val anchor = best?.let { b -> lines.firstOrNull { it.text == b.line } }
        val name = guessName(anchor?.let { a -> lines.filter { a.height <= 0 || kotlin.math.abs(it.centerY - a.centerY) <= a.height * 3.5f } } ?: lines, best?.line)
        val size = lines.firstNotNullOfOrNull { UnitParser.parse(it.text)?.sourceText }
        val onSale = lines.any { saleHint.containsMatchIn(it.text) }
        return ShelfTagResult(best?.cents, name, size, onSale, candidates.sortedByDescending { it.score })
    }

    fun parseText(text: String): ShelfTagResult =
        parse(text.lines().filter { it.isNotBlank() }.map { OcrLine(it) })

    /** Returns (cents, score bonus) for every price-looking token on a line. */
    internal fun candidatesIn(text: String): List<Pair<Long, Float>> {
        val out = mutableListOf<Pair<Long, Float>>()
        val consumed = mutableListOf<IntRange>()
        fun free(r: IntRange) = consumed.none { it.first <= r.last && r.first <= it.last }

        for (re in listOf(multiBuyFor, multiBuySlash)) {
            re.findAll(text).forEach { m ->
                val qty = m.groupValues[1].toInt()
                val total = Money.parse(m.groupValues[2])
                if (qty > 0 && total != null && free(m.range)) {
                    out += ((total + qty / 2) / qty) to 0.4f
                    consumed += m.range
                }
            }
        }
        decimal.findAll(text).forEach { m ->
            if (free(m.range)) {
                val c = m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()
                out += c to (if (m.value.contains('$')) 0.3f else 0.1f)
                consumed += m.range
            }
        }
        dollarSpaceCents.findAll(text).forEach { m ->
            if (free(m.range)) {
                out += (m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()) to 0.25f
                consumed += m.range
            }
        }
        dollarWhole.findAll(text).forEach { m ->
            if (free(m.range)) {
                out += (if (m.groupValues[1].length >= 3) m.groupValues[1].toLong() else m.groupValues[1].toLong() * 100) to 0.15f
                consumed += m.range
            }
        }
        bareSpaced.find(text)?.takeIf { free(it.range) }?.let { m -> out += (m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()) to 0.2f; consumed += m.range }
        bareRun.find(text)?.takeIf { free(it.range) }?.let { m -> out += m.groupValues[1].toLong() to -0.05f; consumed += m.range }
        cents.findAll(text).forEach { m ->
            if (free(m.range)) {
                out += m.groupValues[1].toLong() to 0.1f
                consumed += m.range
            }
        }
        return out
    }

    internal fun joinSuperscript(lines: List<OcrLine>): List<OcrLine> {
        val used = mutableSetOf<OcrLine>()
        val joined = lines.mapNotNull { a ->
            if (a in used) return@mapNotNull null
            val d = splitDollars.find(a.text)?.groupValues?.get(1) ?: return@mapNotNull a
            val b = lines.filter { it !== a && it !in used && a.height > 0 && it.height in 1 until (a.height * 0.8f).toInt().coerceAtLeast(2) && it.left >= a.right - a.height / 3 && it.left - a.right <= a.height && it.centerY in (a.top - a.height * 0.3f)..(a.centerY + a.height * 0.15f) }.firstNotNullOfOrNull { c -> splitCents.find(c.text)?.let { c to it.groupValues[1] } } ?: return@mapNotNull a
            used += b.first
            OcrLine("$$d.${b.second}", a.left, a.top, b.first.right, a.bottom)
        }
        return joined.filter { it !in used }
    }
    private fun guessName(lines: List<OcrLine>, priceLine: String?): String? {
        return lines
            .asSequence()
            .filter { it.text != priceLine }
            .filterNot { unitPriceHint.containsMatchIn(it.text) || noiseHint.containsMatchIn(it.text) || nameNoise.containsMatchIn(it.text) }
            .filterNot { saleHint.matches(it.text.trim()) || barcodeStripes.matches(it.text.trim()) }
            .map { it to it.text.count(Char::isLetter) }
            .filter { (line, letters) -> letters >= 3 && letters.toFloat() / line.text.length > 0.5f }
            .maxByOrNull { (line, letters) -> (line.height.coerceAtLeast(1)) * 10 + letters }
            ?.first?.text?.trim()
    }
}
