package com.amniscient.price.domain

import java.util.Locale

enum class BaseUnit { GRAM, MILLILITER, COUNT }

/** A package size converted to a base unit (grams, milliliters or item count). */
data class Quantity(val amount: Double, val base: BaseUnit, val sourceText: String)

object UnitParser {
    private val pattern = Regex(
        """(\d+(?:[.,]\d+)?)\s*(fl\.?\s*oz|oz|lbs?|kg|g|gr|mg|ml|l|ltr|liters?|litres?|gal|qt|pt|ct|count|pk|pack|ea)\b""",
        RegexOption.IGNORE_CASE,
    )

    // To support a new unit, add it to the regex above and a factor here.
    private fun factor(unit: String): Pair<Double, BaseUnit>? {
        val u = unit.lowercase(Locale.ROOT).replace(".", "").replace(" ", "")
        return when (u) {
            "floz" -> 29.5735 to BaseUnit.MILLILITER
            "oz" -> 28.3495 to BaseUnit.GRAM
            "lb", "lbs" -> 453.592 to BaseUnit.GRAM
            "kg" -> 1000.0 to BaseUnit.GRAM
            "g", "gr" -> 1.0 to BaseUnit.GRAM
            "mg" -> 0.001 to BaseUnit.GRAM
            "ml" -> 1.0 to BaseUnit.MILLILITER
            "l", "ltr", "liter", "liters", "litre", "litres" -> 1000.0 to BaseUnit.MILLILITER
            "gal" -> 3785.41 to BaseUnit.MILLILITER
            "qt" -> 946.353 to BaseUnit.MILLILITER
            "pt" -> 473.176 to BaseUnit.MILLILITER
            "ct", "count", "pk", "pack", "ea" -> 1.0 to BaseUnit.COUNT
            else -> null
        }
    }

    fun parse(text: String?): Quantity? {
        if (text.isNullOrBlank()) return null
        val match = pattern.find(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val (f, base) = factor(match.groupValues[2]) ?: return null
        return Quantity(amount * f, base, match.value.trim())
    }

    /** "$0.25 / oz", "$0.88 / 100 ml", "$0.50 / ea". Imperial in the US, metric elsewhere. */
    fun unitPriceLabel(priceCents: Long, quantity: Quantity, locale: Locale = Locale.getDefault()): String {
        val imperial = locale.country == "US"
        val (perAmount, label) = when (quantity.base) {
            BaseUnit.GRAM -> if (imperial) 28.3495 to "oz" else 100.0 to "100 g"
            BaseUnit.MILLILITER -> if (imperial) 29.5735 to "fl oz" else 100.0 to "100 ml"
            BaseUnit.COUNT -> 1.0 to "ea"
        }
        val cents = Math.round(priceCents * perAmount / quantity.amount)
        return "${Money.format(cents)} / $label"
    }

    /** Price per base unit, used to compare different package sizes of the same product. */
    fun pricePerBase(priceCents: Long, quantity: Quantity): Double = priceCents / quantity.amount
}
