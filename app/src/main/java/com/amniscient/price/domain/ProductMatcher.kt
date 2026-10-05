package com.amniscient.price.domain

/**
 * Fuzzy matching between how a product appears on a receipt ("GV WHL MLK 1G") and the
 * name it was saved under ("Great Value Whole Milk"). Handles prefixes ("CHKN" → chicken),
 * dropped vowels ("WHL" → whole) and brand initialisms ("GV" → great value).
 */
object ProductMatcher {
    const val DEFAULT_THRESHOLD = 0.6

    fun <T> best(query: String, candidates: List<T>, threshold: Double = DEFAULT_THRESHOLD, name: (T) -> String): Pair<T, Double>? =
        candidates
            .map { it to score(query, name(it)) }
            .filter { it.second >= threshold }
            .maxByOrNull { it.second }

    fun score(query: String, candidate: String): Double {
        val q = tokens(query)
        val c = tokens(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0

        val used = BooleanArray(c.size)
        var total = 0.0
        for (token in q) {
            total += matchToken(token, c, used)
        }
        val queryScore = total / q.size
        val coverage = used.count { it }.toDouble() / c.size
        return 0.7 * queryScore + 0.3 * coverage
    }

    private fun matchToken(token: String, candidate: List<String>, used: BooleanArray): Double {
        var bestScore = 0.0
        var bestIdx = -1
        for ((i, c) in candidate.withIndex()) {
            if (used[i]) continue
            val s = when {
                token == c -> 1.0
                token.all(Char::isDigit) || c.all(Char::isDigit) -> 0.0
                token.length >= 2 && c.startsWith(token) -> 0.9
                token.length >= 2 && token[0] == c[0] && isSubsequence(token, c) -> 0.75
                c.length >= 3 && token.startsWith(c) -> 0.7
                else -> 0.0
            }
            if (s > bestScore) {
                bestScore = s
                bestIdx = i
            }
        }
        // Brand initialisms: "gv" → "great value".
        if (bestScore < 0.8 && token.length in 2..4 && token.all(Char::isLetter)) {
            for (start in 0..candidate.size - token.length) {
                val range = start until start + token.length
                if (range.none { used[it] } && range.withIndex().all { (k, idx) -> candidate[idx][0] == token[k] }) {
                    range.forEach { used[it] = true }
                    return 0.8
                }
            }
        }
        if (bestIdx >= 0) used[bestIdx] = true
        return bestScore
    }

    private fun isSubsequence(short: String, long: String): Boolean {
        var i = 0
        for (ch in long) if (i < short.length && short[i] == ch) i++
        return i == short.length
    }

    // "12CT" → "12 ct" so pack sizes line up with "12 ct" in saved names.
    private val digitLetter = Regex("(?<=\\d)(?=\\p{L})|(?<=\\p{L})(?=\\d)")

    private fun tokens(s: String): List<String> =
        normalizeName(s).replace(digitLetter, " ").split(' ').filter { it.length >= 2 || it.all(Char::isDigit) && it.isNotEmpty() }
}
