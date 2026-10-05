package com.amniscient.price.domain

import java.util.Locale

/** Normalized form used to match the same product typed/scanned slightly differently. */
fun normalizeName(name: String): String =
    name.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
