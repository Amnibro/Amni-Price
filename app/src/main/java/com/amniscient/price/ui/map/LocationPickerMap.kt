package com.amniscient.price.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.amniscient.price.domain.LatLng
import com.amniscient.price.map.MapTiles
import com.amniscient.price.ui.theme.Amni
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Handle to read the map center and move the camera from the picker screen. */
class PickerController {
    internal var map: MapView? = null
    val center: LatLng?
        get() = map?.mapCenter?.let { LatLng(it.latitude, it.longitude) }

    fun moveTo(position: LatLng, zoom: Double = 17.0) {
        map?.controller?.animateTo(GeoPoint(position.lat, position.lng), zoom, 400L)
    }
}

/** A plain pan-and-zoom map; the screen draws a fixed crosshair over its center. */
@Composable
fun LocationPickerMap(
    start: LatLng?,
    controller: PickerController,
    modifier: Modifier = Modifier,
    tilesOnline: Boolean = true,
) {
    val context = LocalContext.current
    val palette = Amni.palette
    val map = remember {
        MapView(context).apply {
            setTileSource(MapTiles.source)
            setUseDataConnection(tilesOnline)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            this.controller.setZoom(if (start != null) 17.0 else 4.0)
            this.controller.setCenter(start?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(39.5, -98.35))
        }
    }
    DisposableEffect(Unit) {
        controller.map = map
        onDispose {
            controller.map = null
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier.clipToBounds(),
        update = {
            it.overlayManager.tilesOverlay.setColorFilter(MapTiles.filter(palette.isDark))
            // Offline (tests, or tiles disabled): show the plain brand surface instead of placeholder tiles.
            it.overlayManager.tilesOverlay.isEnabled = tilesOnline
            it.overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            it.overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.TRANSPARENT
            it.setBackgroundColor(palette.panel.toArgb())
        },
    )
}
