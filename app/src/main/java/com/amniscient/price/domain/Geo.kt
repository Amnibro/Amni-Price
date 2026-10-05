package com.amniscient.price.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters. */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** Standard geohash. Precision 5 ≈ a 4.9 × 4.9 km cell, 6 ≈ 1.2 × 0.6 km. */
    fun geohash(p: LatLng, precision: Int = 5): String {
        var latLo = -90.0; var latHi = 90.0
        var lngLo = -180.0; var lngHi = 180.0
        val out = StringBuilder()
        var bit = 0; var ch = 0; var even = true
        while (out.length < precision) {
            if (even) {
                val mid = (lngLo + lngHi) / 2
                if (p.lng >= mid) { ch = ch or (1 shl (4 - bit)); lngLo = mid } else lngHi = mid
            } else {
                val mid = (latLo + latHi) / 2
                if (p.lat >= mid) { ch = ch or (1 shl (4 - bit)); latLo = mid } else latHi = mid
            }
            even = !even
            if (bit < 4) bit++ else { out.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return out.toString()
    }

    fun centroid(points: List<LatLng>): LatLng =
        LatLng(points.map { it.lat }.average(), points.map { it.lng }.average())

    fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) {
        val miles = meters / 1609.344
        if (miles < 0.1) "${(meters * 3.28084).toInt()} ft" else "%.1f mi".format(miles)
    } else {
        if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
    }
}
