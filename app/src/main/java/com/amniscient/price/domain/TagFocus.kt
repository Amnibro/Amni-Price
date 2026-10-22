package com.amniscient.price.domain
import kotlin.math.abs
import kotlin.math.hypot
object TagFocus {
    data class Code(val value: String, val x: Float, val y: Float)
    data class Pick(val lines: List<OcrLine>, val barcode: String?)
    fun pick(lines: List<OcrLine>, codes: List<Code>, w: Int, h: Int): Pick {
        if (w <= 0 || h <= 0) return Pick(lines, codes.firstOrNull()?.value)
        val cx = w / 2f; val cy = h / 2f
        val near = { x: Float, y: Float, fx: Float, fy: Float -> abs(x - cx) <= w * fx && abs(y - cy) <= h * fy }
        val box = lines.filter { near((it.left + it.right) / 2f, it.centerY, 0.4f, 0.28f) }
        val a = box.filter { it.height > 0 && PriceParser.candidatesIn(it.text).isNotEmpty() }.minByOrNull { hypot((it.left + it.right) / 2f - cx, it.centerY - cy) / it.height }
        val ax = a?.let { (it.left + it.right) / 2f } ?: cx; val ay = a?.centerY ?: cy
        val reach = a?.let { maxOf((it.right - it.left) * 1.6f, w * 0.18f) } ?: w.toFloat()
        val tag = a?.let { b -> box.filter { abs((it.left + it.right) / 2f - ax) <= reach && abs(it.centerY - ay) <= b.height * 4f } } ?: box
        return Pick(tag, codes.filter { near(it.x, it.y, 0.45f, 0.38f) && abs(it.x - ax) <= reach * 1.3f }.minByOrNull { hypot(it.x - ax, it.y - ay) }?.value)
    }
    fun gtins(code: String): List<String> = code.filter(Char::isDigit).let { d -> (listOfNotNull(code, d.takeIf { it.length == 12 }?.let { "0$it" }, d.takeIf { it.length == 13 && it.startsWith('0') }?.drop(1)) + (upcE(d)?.let { listOf(it, "0$it") } ?: emptyList())).distinct() }
    fun upcE(d: String): String? {
        if (d.length != 8 || d[0] !in "01") return null
        val m = d.substring(1, 7); val l = m[5]
        val body = when (l) { '0', '1', '2' -> "${m.substring(0, 2)}${l}0000${m.substring(2, 5)}"; '3' -> "${m.substring(0, 3)}00000${m.substring(3, 5)}"; '4' -> "${m.substring(0, 4)}00000${m[4]}"; else -> "${m.substring(0, 5)}0000$l" }
        return "${d[0]}$body${d[7]}"
    }
}
