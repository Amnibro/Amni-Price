package com.amniscient.price.domain

import java.time.LocalDate

data class ReceiptItem(
    val name: String,
    val priceCents: Long,
    val quantity: Int = 1,
    val discounted: Boolean = false,
) {
    /** Price for a single unit (receipts print line totals for "2 @ 1.99"). */
    val unitPriceCents: Long get() = if (quantity > 1) (priceCents + quantity / 2) / quantity else priceCents
}

data class ReceiptResult(
    val storeName: String?,
    val items: List<ReceiptItem>,
    val totalCents: Long?,
    val date: LocalDate? = null,
)

object ReceiptParser {
    private val opts = setOf(RegexOption.IGNORE_CASE)
    private val itemLine = Regex("""^(.*?[A-Za-z].*?)\s+(-)?\$?\s*(\d{1,4}[.,]\d{2})\s*(-)?\s*(?:[A-Z]{1,2}|\*)?\s*$""")
    private val qtyLine = Regex("""(\d{1,3})\s*@\s*\$?\s*(\d{1,4}[.,]\d{2})""")
    private val skip = Regex(
        """\b(sub\s*-?total|total|tax|hst|gst|vat|change|cash|tender|visa|mastercard|amex|debit|credit|balance|""" +
            """card|auth|approval|ref\s*#|terminal|savings|you\s+saved|points|rewards|tip|due|paid|payment|""" +
            """items\s+sold|thank|receipt|survey|member|loyalty)\b""",
        opts,
    )
    private val total = Regex("""(?<!sub)(?<!sub\s)\btotal\b""", opts)
    private val itemCode = Regex("""\b\d{5,14}\b""")
    private val trailingFlag = Regex("""\s+[A-Z]{1,2}$""")

    fun parse(rows: List<String>): ReceiptResult {
        val items = mutableListOf<ReceiptItem>()
        var pendingQty: Int? = null
        var totalCents: Long? = null

        for (raw in rows) {
            val row = raw.trim()
            if (row.isEmpty()) continue

            if (skip.containsMatchIn(row)) {
                if (total.containsMatchIn(row) && totalCents == null) {
                    totalCents = Regex("""(\d{1,5}[.,]\d{2})""").findAll(row).lastOrNull()?.let { Money.parse(it.value) }
                }
                continue
            }

            val qty = qtyLine.find(row)
            val m = itemLine.find(row)
            if (m == null) {
                if (qty != null) {
                    val q = qty.groupValues[1].toInt()
                    val lineTotal = Money.parse(qty.groupValues[2])?.times(q)
                    // "2 @ 1.99" on its own row: stores print it either above or below the item.
                    // Attach it to the previous item only if that item's total matches.
                    val last = items.lastOrNull()
                    if (last != null && last.quantity == 1 && q > 1 && last.priceCents == lineTotal) {
                        items[items.lastIndex] = last.copy(quantity = q)
                    } else {
                        pendingQty = q
                    }
                }
                continue
            }

            val negative = m.groupValues[2].isNotEmpty() || m.groupValues[4].isNotEmpty()
            val cents = Money.parse(m.groupValues[3]) ?: continue
            if (negative) {
                // Coupons / instant savings: apply to the previous item.
                val last = items.lastOrNull() ?: continue
                items[items.lastIndex] = last.copy(
                    priceCents = (last.priceCents - cents).coerceAtLeast(0),
                    discounted = true,
                )
                continue
            }

            val name = cleanName(qtyLine.replace(m.groupValues[1], ""))
            if (name.count(Char::isLetter) < 2) continue
            items += ReceiptItem(name, cents, quantity = qty?.groupValues?.get(1)?.toInt() ?: pendingQty ?: 1)
            pendingQty = null
        }

        return ReceiptResult(guessStore(rows), items, totalCents, rows.firstNotNullOfOrNull(::parseDate))
    }

    private val usDate = Regex("""(?<![\d.])(\d{1,2})[/-](\d{1,2})[/-](\d{4}|\d{2})(?![\d.])""")
    private val isoDate = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")

    /** Receipt dates: 10/05/26, 10-05-2026, 2026-10-05, and day-first when the first number is > 12. */
    internal fun parseDate(row: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val candidates = buildList {
            isoDate.find(row)?.let { m -> add(Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())) }
            usDate.find(row)?.let { m ->
                var month = m.groupValues[1].toInt()
                var day = m.groupValues[2].toInt()
                val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                if (month > 12 && day <= 12) month = day.also { day = month }
                add(Triple(y, month, day))
            }
        }
        return candidates.firstNotNullOfOrNull { (y, mo, d) ->
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()
                ?.takeIf { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }
        }
    }

    internal fun cleanName(name: String): String =
        name.replace(itemCode, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .replace(trailingFlag, "")
            .trim(' ', '-', '*', '#', ':')

    private fun guessStore(rows: List<String>): String? =
        rows.take(5)
            .map { it.trim() }
            .firstOrNull { row ->
                row.count(Char::isLetter) >= 3 &&
                    !skip.containsMatchIn(row) &&
                    !itemLine.matches(row) &&
                    !Regex("""^\d""").containsMatchIn(row)
            }
}
