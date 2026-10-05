package com.amniscient.price.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/** All prices are stored as integer cents to avoid floating point drift. */
object Money {
    fun format(cents: Long): String = NumberFormat.getCurrencyInstance().format(cents / 100.0)

    /** Parses user/OCR text like "$3.99", "3,99", "1,299.00" into cents. */
    fun parse(text: String): Long? {
        var cleaned = text.replace(Regex("[^0-9.,-]"), "")
        if (cleaned.isEmpty()) return null
        cleaned = when {
            cleaned.contains('.') && cleaned.contains(',') -> cleaned.replace(",", "")
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun toPlain(cents: Long): String = BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()
}
