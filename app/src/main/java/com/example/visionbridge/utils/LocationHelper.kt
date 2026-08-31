package com.example.visionbridge.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    // Default Pune fallback coordinates matching MERN backend fallback
    const val DEFAULT_LATITUDE = 18.5204
    const val DEFAULT_LONGITUDE = 73.8567

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double> {
        if (!hasLocationPermission(context)) {
            return Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            val location: Location? = suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { loc ->
                    continuation.resume(loc)
                }.addOnFailureListener {
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            }

            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                getLastKnownLocation(context) ?: Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
            }
        } catch (e: Exception) {
            getLastKnownLocation(context) ?: Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(context: Context): Pair<Double, Double>? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }

            bestLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }
}
