package com.sih.drugtestclassifier.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Enhanced Location and Reverse-Geocoding service.
 * Supports active single location updates, last known location caching,
 * reverse geocoding into exact street/neighborhood/city names (e.g. "Makhmalabad, Nashik"),
 * and EXIF metadata extraction for uploaded inspection photos.
 */
object LocationHelper {

    data class LatLon(
        val latitude: Double,
        val longitude: Double,
        val available: Boolean,
        val address: String = "",
    )

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Synchronous best-effort last known location fix with reverse-geocoded address.
     */
    fun lastKnown(context: Context): LatLon {
        if (!hasPermission(context)) {
            return LatLon(0.0, 0.0, available = false, address = "Permission not granted")
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LatLon(0.0, 0.0, available = false, address = "Location service unavailable")

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    val location = manager.getLastKnownLocation(provider)
                    if (location != null) {
                        if (bestLocation == null || location.time > bestLocation.time) {
                            bestLocation = location
                        }
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }

        return if (bestLocation != null) {
            val addr = resolveAddress(context, bestLocation.latitude, bestLocation.longitude)
            LatLon(bestLocation.latitude, bestLocation.longitude, available = true, address = addr)
        } else {
            LatLon(0.0, 0.0, available = false, address = "Acquiring GPS fix…")
        }
    }

    /**
     * Actively requests a fresh location fix with a timeout, ensuring we don't
     * return (0.0, 0.0) if a cached location was not yet available.
     */
    suspend fun getFreshLocation(context: Context, timeoutMs: Long = 4000L): LatLon {
        if (!hasPermission(context)) {
            return LatLon(0.0, 0.0, available = false, address = "Location permission required")
        }

        val last = lastKnown(context)
        if (last.available && (last.latitude != 0.0 || last.longitude != 0.0)) {
            return last
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return last

        val freshLocation = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                        try {
                            manager.removeUpdates(this)
                        } catch (_: Exception) {}
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    val enabledProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                        .filter { manager.isProviderEnabled(it) }

                    if (enabledProviders.isEmpty()) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    for (p in enabledProviders) {
                        manager.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                    }

                    cont.invokeOnCancellation {
                        try {
                            manager.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        return if (freshLocation != null) {
            val addr = resolveAddress(context, freshLocation.latitude, freshLocation.longitude)
            LatLon(freshLocation.latitude, freshLocation.longitude, available = true, address = addr)
        } else {
            last
        }
    }

    /**
     * Reverse geocodes latitude/longitude into a descriptive human-readable place name.
     * e.g., "Madhur Sweets, College Road, Nashik" or "Makhmalabad, Nashik".
     */
    fun resolveAddress(context: Context, latitude: Double, longitude: Double): String {
        if (latitude == 0.0 && longitude == 0.0) {
            return "Coordinates unavailable (0.0, 0.0)"
        }

        if (!Geocoder.isPresent()) {
            return String.format(Locale.US, "Lat: %.4f, Lon: %.4f", latitude, longitude)
        }

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val parts = mutableListOf<String>()

                val feature = addr.featureName
                val thoroughfare = addr.thoroughfare
                val subLocality = addr.subLocality ?: addr.subAdminArea
                val locality = addr.locality ?: addr.adminArea

                if (!feature.isNullOrBlank() && feature != thoroughfare && feature != subLocality && feature != locality) {
                    parts.add(feature)
                }
                if (!thoroughfare.isNullOrBlank() && !parts.contains(thoroughfare)) {
                    parts.add(thoroughfare)
                }
                if (!subLocality.isNullOrBlank() && !parts.contains(subLocality)) {
                    parts.add(subLocality)
                }
                if (!locality.isNullOrBlank() && !parts.contains(locality)) {
                    parts.add(locality)
                }

                if (parts.isNotEmpty()) {
                    parts.joinToString(", ")
                } else if (!addr.getAddressLine(0).isNullOrBlank()) {
                    addr.getAddressLine(0)
                } else {
                    String.format(Locale.US, "Lat: %.4f, Lon: %.4f", latitude, longitude)
                }
            } else {
                String.format(Locale.US, "Lat: %.4f, Lon: %.4f", latitude, longitude)
            }
        } catch (_: Exception) {
            String.format(Locale.US, "Lat: %.4f, Lon: %.4f", latitude, longitude)
        }
    }

    /**
     * Extracts EXIF location and original capture date from an image file/content URI.
     */
    suspend fun extractExif(context: Context, uri: Uri): Pair<LatLon?, Long?> = withContext(Dispatchers.IO) {
        try {
            val inputStream = if (uri.scheme == "file") {
                File(uri.path ?: return@withContext Pair(null, null)).inputStream()
            } else {
                context.contentResolver.openInputStream(uri) ?: return@withContext Pair(null, null)
            }

            inputStream.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                @Suppress("DEPRECATION")
                val hasGps = exif.getLatLong(latLong)

                val latLonResult = if (hasGps) {
                    val lat = latLong[0].toDouble()
                    val lon = latLong[1].toDouble()
                    val addr = resolveAddress(context, lat, lon)
                    LatLon(lat, lon, available = true, address = "$addr (From Photo EXIF)")
                } else null

                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                val parsedTime = dateStr?.let {
                    try {
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time
                    } catch (_: Exception) {
                        null
                    }
                }

                Pair(latLonResult, parsedTime)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }
}
