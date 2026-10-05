package com.amniscient.price.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.amniscient.price.data.PriceWithStore
import com.amniscient.price.domain.Money
import com.amniscient.price.ui.components.Eyebrow
import com.amniscient.price.ui.components.Panel
import com.amniscient.price.ui.components.PriceText
import com.amniscient.price.ui.theme.Amni
import com.amniscient.price.ui.theme.AmniText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class Series(val storeId: Long, val name: String, val color: Color, val points: List<Pair<Long, Long>>) {
    /** The price in effect at [t]: the last observation at or before it. */
    fun valueAt(t: Long): Long? = points.lastOrNull { it.first <= t }?.second
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d")
private fun Long.day(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dayFormat)

/**
 * Price history per store as step lines: a shelf price holds until it is next observed.
 * Color follows the store (fixed slot by store id), never its rank. Drag to scrub.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceChart(history: List<PriceWithStore>, modifier: Modifier = Modifier) {
    val palette = Amni.palette
    val byStore = history.groupBy { it.price.storeId }
    val storeIds = byStore.keys.sorted()
    val shown = storeIds.take(palette.series.size)
    val series = shown.mapIndexed { i, id ->
        val rows = byStore.getValue(id).sortedBy { it.price.observedAt }
        Series(id, rows.first().storeName, palette.series[i], rows.map { it.price.observedAt to it.price.priceCents })
    }
    val allPoints = series.flatMap { it.points }
    if (allPoints.size < 2) {
        Text(
            "Log this product again, or at another store, to see its price history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }

    val tMin = allPoints.minOf { it.first }
    val tMax = maxOf(allPoints.maxOf { it.first }, tMin + 1)
    val pMinRaw = allPoints.minOf { it.second }
    val pMaxRaw = allPoints.maxOf { it.second }
    val pad = maxOf((pMaxRaw - pMinRaw) / 8, pMaxRaw / 20, 5)
    val pMin = (pMinRaw - pad).coerceAtLeast(0)
    val pMax = pMaxRaw + pad

    var scrubX by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val geometry = with(density) { ChartGeometry(48.dp.toPx(), widthPx - 8.dp.toPx(), tMin, tMax) }
    val measurer = rememberTextMeasurer()
    val labelStyle = AmniText.priceSmall.copy(color = palette.muted, fontSize = AmniText.priceSmall.fontSize * 0.85f)
    val surface = palette.panel
    val hairline = palette.hairline
    val ink = palette.ink

    Panel(modifier) {
        Column(Modifier.padding(12.dp)) {
            Box {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .onSizeChanged { widthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { scrubX = it.x },
                                onDragEnd = { scrubX = null },
                                onDragCancel = { scrubX = null },
                            ) { change, _ -> scrubX = change.position.x }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                scrubX = it.x
                                tryAwaitRelease()
                                scrubX = null
                            })
                        },
                ) {
                    val g = ChartGeometry(48.dp.toPx(), size.width - 8.dp.toPx(), tMin, tMax)
                    val left = g.left
                    val right = g.right
                    val bottom = size.height - 20.dp.toPx()
                    val top = 8.dp.toPx()
                    fun x(t: Long) = g.x(t)
                    fun y(p: Long) = bottom - (p - pMin).toFloat() / (pMax - pMin) * (bottom - top)

                    // Recessive grid: three hairlines with mono labels.
                    for (k in 0..2) {
                        val value = pMin + (pMax - pMin) * k / 2
                        val yy = y(value)
                        drawLine(hairline, Offset(left, yy), Offset(right, yy), 1.dp.toPx())
                        val label = measurer.measure(Money.format(value), labelStyle)
                        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2))
                    }
                    listOf(tMin to 0f, tMax to 1f).forEach { (t, align) ->
                        val label = measurer.measure(t.day(), labelStyle)
                        drawText(label, topLeft = Offset(x(t) - label.size.width * align, bottom + 4.dp.toPx()))
                    }

                    series.forEach { s ->
                        val path = Path()
                        s.points.forEachIndexed { i, (t, p) ->
                            if (i == 0) path.moveTo(x(t), y(p)) else {
                                path.lineTo(x(t), y(s.points[i - 1].second))
                                path.lineTo(x(t), y(p))
                            }
                        }
                        path.lineTo(x(tMax), y(s.points.last().second))
                        drawPath(path, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        // Observation markers (8dp) with a 2dp surface ring so overlaps stay legible.
                        val markers = if (s.points.size <= 12) s.points else listOf(s.points.last())
                        markers.forEach { (t, p) ->
                            drawCircle(surface, 6.dp.toPx(), Offset(x(t), y(p)))
                            drawCircle(s.color, 4.dp.toPx(), Offset(x(t), y(p)))
                        }
                    }

                    scrubX?.let { sx ->
                        val t = g.timeAt(sx)
                        val cx = sx.coerceIn(left, right)
                        drawLine(ink.copy(alpha = 0.5f), Offset(cx, top), Offset(cx, bottom), 1.dp.toPx())
                        series.forEach { s ->
                            s.valueAt(t)?.let { p ->
                                drawCircle(surface, 6.dp.toPx(), Offset(cx, y(p)))
                                drawCircle(s.color, 4.dp.toPx(), Offset(cx, y(p)))
                            }
                        }
                    }
                }

                scrubX?.let { sx ->
                    // Tooltip: date + each store's price in effect, cheapest first. Text stays in ink.
                    Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp)) {
                        Column(
                            Modifier
                                .background(palette.panel2, MaterialTheme.shapes.small)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            val t = geometry.timeAt(sx)
                            Eyebrow(t.day())
                            series.mapNotNull { s -> s.valueAt(t)?.let { s to it } }.sortedBy { it.second }.forEach { (s, p) ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).background(s.color))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.name, style = MaterialTheme.typography.bodySmall, color = ink)
                                    Spacer(Modifier.width(8.dp))
                                    PriceText(p, style = AmniText.priceSmall, color = ink)
                                }
                            }
                        }
                    }
                }
            }

            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                series.forEach { s ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(s.color, MaterialTheme.shapes.extraSmall))
                        Spacer(Modifier.width(6.dp))
                        Text(s.name, style = MaterialTheme.typography.bodySmall, color = ink)
                        Spacer(Modifier.width(4.dp))
                        PriceText(s.points.last().second, style = AmniText.priceSmall, color = palette.muted)
                    }
                }
            }
            if (storeIds.size > shown.size) {
                Text(
                    "+${storeIds.size - shown.size} more stores listed below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Shared x-geometry between drawing and the tooltip, so both agree on the scrubbed time. */
private class ChartGeometry(val left: Float, val right: Float, val tMin: Long, val tMax: Long) {
    fun x(t: Long) = left + (t - tMin).toFloat() / (tMax - tMin) * (right - left)
    fun timeAt(px: Float): Long {
        val cx = px.coerceIn(left, right)
        return tMin + ((cx - left) / (right - left) * (tMax - tMin)).toLong()
    }
}
