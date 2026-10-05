package com.amniscient.price.map

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.amniscient.price.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.File

/**
 * Map tiles. The URL template and attribution come from the build (`amni.mapTileUrl`,
 * `amni.mapAttribution` in gradle.properties or local.properties).
 *
 * The default uses OpenStreetMap's public tile servers, which are for development and light
 * use only (https://operations.osmfoundation.org/policies/tiles/). Before publishing, point
 * `amni.mapTileUrl` at a tile provider you have an account with (MapTiler, Stadia, Thunderforest…).
 */
object MapTiles {
    val source: OnlineTileSourceBase = TemplateTileSource(BuildConfig.MAP_TILE_URL, BuildConfig.MAP_ATTRIBUTION)
    val attribution: String = BuildConfig.MAP_ATTRIBUTION

    fun init(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
    }

    /**
     * The tiles are recolored to match the Amniscient palette: graphite in dark mode (inverted
     * luminance with a cool cast), warm gray in light mode, so prices stay the loudest thing.
     */
    fun filter(dark: Boolean): ColorMatrixColorFilter {
        val r = 0.2126f; val g = 0.7152f; val b = 0.0722f
        val matrix = if (dark) {
            val k = -0.62f
            ColorMatrix(
                floatArrayOf(
                    k * r, k * g, k * b, 0f, 172f,
                    k * r, k * g, k * b, 0f, 176f,
                    k * r, k * g, k * b, 0f, 184f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        } else {
            ColorMatrix().apply { setSaturation(0.12f) }.also { sat ->
                sat.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.92f, 0f, 0f, 0f, 14f,
                            0f, 0.92f, 0f, 0f, 13f,
                            0f, 0f, 0.92f, 0f, 9f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
        }
        return ColorMatrixColorFilter(matrix)
    }
}

/** A tile source from a `{z}/{x}/{y}` URL template (keys can be embedded in the template). */
private class TemplateTileSource(private val template: String, attribution: String) :
    OnlineTileSourceBase("amni", 2, 19, 256, "", arrayOf(template), attribution) {
    override fun getTileURLString(index: Long): String =
        template
            .replace("{z}", MapTileIndex.getZoom(index).toString())
            .replace("{x}", MapTileIndex.getX(index).toString())
            .replace("{y}", MapTileIndex.getY(index).toString())
}
