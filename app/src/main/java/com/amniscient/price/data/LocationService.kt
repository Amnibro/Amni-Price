package com.amniscient.price.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.amniscient.price.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Device location via the platform LocationManager (no Google Play Services needed), plus
 * best-effort reverse geocoding to a town name for grouping stores into regions.
 * Your position is only used on the phone: it's never uploaded.
 */
class LocationService(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix if one arrives within [timeoutMs], else the most recent known location. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 8_000): LatLng? {
        if (!hasPermission()) return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val fresh = if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(providers.first(), signal, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else {
            null
        }
        val best = fresh ?: lastKnown()
        return best?.let { LatLng(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? =
        runCatching { manager.allProviders.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()

    /** The town / area for a position, e.g. "Dublin". Null when no geocoder is available or offline. */
    suspend fun regionFor(position: LatLng): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(position.lat, position.lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(position.lat, position.lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.let { it.locality ?: it.subAdminArea ?: it.subLocality ?: it.adminArea }
    }

    /** Current position plus its town name, for pinning a store you're standing in. */
    suspend fun hereWithRegion(): Pair<LatLng, String?>? {
        val here = current() ?: return null
        return here to regionFor(here)
    }
}
