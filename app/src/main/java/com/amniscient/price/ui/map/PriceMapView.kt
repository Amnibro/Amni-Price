package com.amniscient.price.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amniscient.price.R
import com.amniscient.price.domain.LatLng
import com.amniscient.price.domain.RegionStat
import com.amniscient.price.domain.RegionalPrices
import com.amniscient.price.domain.RegionalPrices.PriceBand
import com.amniscient.price.map.MapTiles
import com.amniscient.price.ui.theme.Amni
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** A store pin: where it is and what to print on it. */
data class MapPin(val storeId: Long, val position: LatLng, val label: String, val value: Double)

data class MapRegion(val stat: RegionStat, val label: String)

/** Colors the overlay draws with, resolved from the Amni palette for the current theme. */
private data class PinColors(
    val deal: Int, val onDeal: Int, val rise: Int, val ink: Int, val panel: Int, val brass: Int, val hairline: Int,
)

/**
 * The price map: OpenStreetMap tiles recolored to the brand, price pins banded against the
 * cheapest store (cheapest / within 10% / higher — the label always carries the number, so color
 * is never the only cue), optional region circles, and your position.
 *
 * [fitKey]: the camera fits all pins whenever this changes (e.g. a different product is chosen).
 */
@Composable
fun PriceMapView(
    pins: List<MapPin>,
    regions: List<MapRegion>,
    here: LatLng?,
    selectedStoreId: Long?,
    onSelect: (Long?) -> Unit,
    fitKey: Any?,
    modifier: Modifier = Modifier,
    tilesOnline: Boolean = true,
) {
    val context = LocalContext.current
    val palette = Amni.palette
    val colors = PinColors(
        deal = palette.deal.toArgb(),
        onDeal = MaterialTheme.colorScheme.onPrimary.toArgb(),
        rise = palette.rise.toArgb(),
        ink = palette.ink.toArgb(),
        panel = palette.panel.toArgb(),
        brass = palette.brass.toArgb(),
        hairline = palette.hairline.toArgb(),
    )
    val state = remember { MapState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val map = remember {
        MapView(context).apply {
            setTileSource(MapTiles.source)
            setUseDataConnection(tilesOnline)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(39.5, -98.35))
            overlays.add(PriceOverlay(context, state))
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        modifier = modifier.clipToBounds(),
        update = { view ->
            view.overlayManager.tilesOverlay.setColorFilter(MapTiles.filter(palette.isDark))
            // Offline (tests, or tiles disabled): show the plain brand surface instead of placeholder tiles.
            view.overlayManager.tilesOverlay.isEnabled = tilesOnline
            // Transparent loading tiles: the color filter would otherwise turn them gray.
            view.overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            view.overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
            view.setBackgroundColor(colors.panel)
            state.pins = pins
            state.regions = regions
            state.here = here
            state.selected = selectedStoreId
            state.colors = colors
            state.onSelect = onSelect
            if (state.fittedKey != fitKey && pins.isNotEmpty()) {
                state.fittedKey = fitKey
                fit(view, pins.map { it.position } + listOfNotNull(here))
            }
            view.invalidate()
        },
    )
}

private class MapState {
    var pins: List<MapPin> = emptyList()
    var regions: List<MapRegion> = emptyList()
    var here: LatLng? = null
    var selected: Long? = null
    var colors: PinColors? = null
    var onSelect: (Long?) -> Unit = {}
    var fittedKey: Any? = Unit
}

private fun fit(map: MapView, points: List<LatLng>) {
    val run = {
        if (points.size == 1) {
            map.controller.setZoom(14.0)
            map.controller.setCenter(GeoPoint(points[0].lat, points[0].lng))
        } else {
            val box = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.lat, it.lng) })
            // Pad so pins at the edge aren't clipped, and don't zoom in absurdly far on close stores.
            map.zoomToBoundingBox(box.increaseByScale(1.35f), false, (48 * map.resources.displayMetrics.density).toInt())
            if (map.zoomLevelDouble > 15.5) map.controller.setZoom(15.5)
        }
    }
    if (map.width > 0 && map.height > 0) run() else map.addOnFirstLayoutListener { _, _, _, _, _ -> run() }
}

private class PriceOverlay(context: Context, private val state: MapState) : Overlay() {
    private val density = context.resources.displayMetrics.density
    private val mono: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold) ?: Typeface.MONOSPACE
    private val sans: Typeface = ResourcesCompat.getFont(context, R.font.archivo) ?: Typeface.DEFAULT_BOLD

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 12.5f * density }
    private val regionText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(sans, Typeface.BOLD); textSize = 11f * density; letterSpacing = 0.08f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hits = mutableListOf<Pair<RectF, Long>>()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val colors = state.colors ?: return
        val projection = mapView.projection
        val point = android.graphics.Point()
        hits.clear()

        // Region circles: a translucent wash per area, banded like the pins, labelled at the top.
        val bestRegion = state.regions.minOfOrNull { it.stat.average }
        state.regions.forEach { region ->
            val center = GeoPoint(region.stat.center.lat, region.stat.center.lng)
            projection.toPixels(center, point)
            val edge = android.graphics.Point()
            projection.toPixels(center.destinationPoint(region.stat.radiusMeters + 700.0, 90.0), edge)
            val radius = (edge.x - point.x).toFloat().coerceAtLeast(28 * density)
            val band = bestRegion?.let { RegionalPrices.band(region.stat.average, it) } ?: PriceBand.NEAR
            val c = bandColor(band, colors)
            fill.color = withAlpha(c, if (band == PriceBand.NEAR) 0x10 else 0x1F)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, fill)
            stroke.color = withAlpha(c, 0x80); stroke.strokeWidth = 1.5f * density
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, stroke)

            // Label under the circle: pins grow upward from their stores, so this side stays clear.
            val label = region.label.uppercase()
            val w = regionText.measureText(label)
            val top = point.y + radius + 18 * density
            val box = RectF(point.x - w / 2 - 6 * density, top - 13 * density, point.x + w / 2 + 6 * density, top + 5 * density)
            fill.color = withAlpha(colors.panel, 0xE6)
            canvas.drawRoundRect(box, 3 * density, 3 * density, fill)
            regionText.color = colors.ink
            canvas.drawText(label, point.x - w / 2, top, regionText)
        }

        // Pins: lay out cheapest first (it keeps its spot), then stack any label that would
        // overlap an earlier one above it, with a leader line back to its store's dot.
        val cheapest = state.pins.minOfOrNull { it.value }
        val placed = mutableListOf<RectF>()
        val layout = state.pins.sortedBy { it.value }.map { pin ->
            projection.toPixels(GeoPoint(pin.position.lat, pin.position.lng), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            var lift = 0f
            var rect = labelRect(x, y, pin.label, lift)
            var tries = 0
            while (placed.any { RectF.intersects(it, rect) } && tries < 6) {
                lift += rect.height() + 4 * density
                rect = labelRect(x, y, pin.label, lift)
                tries++
            }
            placed += rect
            PinLayout(pin, x, y, lift)
        }
        // Every store's dot first, so no dot ever lands on top of another store's label.
        layout.forEach { l ->
            val band = cheapest?.let { RegionalPrices.band(l.pin.value, it) } ?: PriceBand.NEAR
            drawDot(canvas, l.x, l.y, band, colors)
        }
        // Labels: expensive first so the cheapest end up on top.
        layout.asReversed().forEach { l ->
            val band = cheapest?.let { RegionalPrices.band(l.pin.value, it) } ?: PriceBand.NEAR
            drawPin(canvas, l.x, l.y, l.lift, l.pin, band, l.pin.storeId == state.selected, colors)
        }

        state.here?.let { h ->
            projection.toPixels(GeoPoint(h.lat, h.lng), point)
            fill.color = withAlpha(colors.brass, 0x33)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 14 * density, fill)
            fill.color = colors.panel
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 7 * density, fill)
            fill.color = colors.brass
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 5 * density, fill)
        }
    }

    private data class PinLayout(val pin: MapPin, val x: Float, val y: Float, val lift: Float)

    private fun labelRect(x: Float, y: Float, label: String, lift: Float): RectF {
        val w = text.measureText(label) + 14 * density
        val bottom = y - 6 * density - lift
        return RectF(x - w / 2, bottom - 22 * density, x + w / 2, bottom)
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, lift: Float, pin: MapPin, band: PriceBand, selected: Boolean, c: PinColors) {
        val padH = 7 * density
        val tip = 6 * density
        val rect = labelRect(x, y, pin.label, lift)
        val accent = bandColor(band, c)
        val tipY = y - lift

        // Leader line back to the store's dot when the label was lifted to avoid an overlap.
        if (lift > 0) {
            stroke.color = withAlpha(accent, 0xAA); stroke.strokeWidth = 1.5f * density
            canvas.drawLine(x, y - 4.5f * density, x, rect.bottom, stroke)
        }

        val shape = Path().apply {
            addRoundRect(rect, 3 * density, 3 * density, Path.Direction.CW)
            if (lift == 0f) {
                moveTo(x - tip, rect.bottom - 1)
                lineTo(x, tipY - 3 * density)
                lineTo(x + tip, rect.bottom - 1)
                close()
            }
        }
        fill.color = if (band == PriceBand.CHEAPEST) c.deal else c.panel
        canvas.drawPath(shape, fill)
        stroke.color = if (selected) c.brass else if (band == PriceBand.CHEAPEST) c.deal else withAlpha(accent, 0xCC)
        stroke.strokeWidth = (if (selected) 2.5f else 1.5f) * density
        canvas.drawPath(shape, stroke)

        text.color = if (band == PriceBand.CHEAPEST) c.onDeal else if (band == PriceBand.HIGHER) c.rise else c.ink
        val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(pin.label, rect.left + padH, baseline, text)

        // Generous hit target: the label plus a margin.
        hits += RectF(rect.left - 6 * density, rect.top - 6 * density, rect.right + 6 * density, rect.bottom + 6 * density) to pin.storeId
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, band: PriceBand, c: PinColors) {
        fill.color = c.panel
        canvas.drawCircle(x, y, 4.5f * density, fill)
        fill.color = bandColor(band, c)
        canvas.drawCircle(x, y, 3f * density, fill)
    }

    private fun bandColor(band: PriceBand, c: PinColors) = when (band) {
        PriceBand.CHEAPEST -> c.deal
        PriceBand.NEAR -> c.ink
        PriceBand.HIGHER -> c.rise
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    @SuppressLint("ClickableViewAccessibility")
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        // Topmost (cheapest, drawn last) first.
        val hit = hits.lastOrNull { it.first.contains(e.x, e.y) }?.second
        state.onSelect(hit)
        return hit != null
    }
}
