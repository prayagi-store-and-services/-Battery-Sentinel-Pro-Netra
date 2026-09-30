package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.LocationContextState
import com.example.model.LocationPermissionState
import java.util.Locale

class LocationCountryResolver(private val context: Context) {

    fun resolveLocationContext(): LocationContextState {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPermission = hasCoarse || hasFine

        var countryCode: String? = null
        var countryName: String? = null
        var regionName: String? = null
        var locality: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var accuracy: Float? = null
        var source: String = "Locale / Telephony"

        // 1. Try Telephony / SIM Country ISO
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.takeIf { it.isNotBlank() }
            val networkCountry = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            countryCode = (networkCountry ?: simCountry)?.uppercase(Locale.ROOT)
            if (countryCode != null) {
                source = "Cellular Network"
            }
        } catch (_: Exception) {}

        // 2. Fallback to Device Default Locale Country
        if (countryCode == null) {
            val locale = Locale.getDefault()
            val locCountry = locale.country?.takeIf { it.isNotBlank() }
            if (locCountry != null) {
                countryCode = locCountry.uppercase(Locale.ROOT)
                countryName = locale.displayCountry.takeIf { it.isNotBlank() }
                source = "Device Locale"
            }
        }

        // 3. If Location Permission is available, obtain last known coarse position and reverse geocode
        val permissionState: LocationPermissionState
        if (!hasPermission) {
            permissionState = LocationPermissionState.LOCATION_PERMISSION_REQUIRED
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            val isNetworkEnabled = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

            if (!isGpsEnabled && !isNetworkEnabled) {
                permissionState = LocationPermissionState.LOCATION_DISABLED
            } else {
                permissionState = LocationPermissionState.LOCATION_AVAILABLE

                val lastLoc: Location? = try {
                    val netLoc = if (isNetworkEnabled) lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
                    val gpsLoc = if (isGpsEnabled) lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
                    val passiveLoc = lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    netLoc ?: gpsLoc ?: passiveLoc
                } catch (_: SecurityException) {
                    null
                }

                if (lastLoc != null) {
                    lat = lastLoc.latitude
                    lon = lastLoc.longitude
                    accuracy = if (lastLoc.hasAccuracy()) lastLoc.accuracy else null
                    source = if (lastLoc.provider != null) "Location Provider (${lastLoc.provider})" else "Location Services"

                    // Reverse geocoding for City, State, Country Name
                    if (Geocoder.isPresent()) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(lastLoc.latitude, lastLoc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                if (!addr.countryCode.isNullOrBlank()) {
                                    countryCode = addr.countryCode.uppercase(Locale.ROOT)
                                }
                                if (!addr.countryName.isNullOrBlank()) {
                                    countryName = addr.countryName
                                }
                                if (!addr.adminArea.isNullOrBlank()) {
                                    regionName = addr.adminArea
                                }
                                if (!addr.locality.isNullOrBlank()) {
                                    locality = addr.locality
                                } else if (!addr.subAdminArea.isNullOrBlank()) {
                                    locality = addr.subAdminArea
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // Final normalization of country name if code is known but name is missing
        if (countryName == null && countryCode != null) {
            try {
                countryName = Locale("", countryCode).displayCountry.takeIf { it.isNotBlank() } ?: countryCode
            } catch (_: Exception) {
                countryName = countryCode
            }
        }

        return LocationContextState(
            countryCode = countryCode,
            countryName = countryName ?: "Unknown",
            regionName = regionName,
            locality = locality,
            locationAccuracyMeters = accuracy,
            locationSource = source,
            latitude = lat,
            longitude = lon,
            permissionState = permissionState,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
