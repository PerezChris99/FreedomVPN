package com.freedomvpn.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FreedomVPN Permission Manager
 * 
 * Handles runtime permission requests for:
 * - Location (Fine & Coarse) - for accurate server selection
 * - Storage - for VPN config caching
 * - Notifications - for connection status
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "PermissionManager"
        
        // All permissions the app needs
        val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        val LOCATION_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    data class PermissionStatus(
        val locationGranted: Boolean,
        val storageGranted: Boolean,
        val notificationsGranted: Boolean,
        val allGranted: Boolean
    )

    /**
     * Check current permission status
     */
    fun checkPermissions(): PermissionStatus {
        val locationGranted = LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true // Scoped storage doesn't need permissions
        } else {
            STORAGE_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return PermissionStatus(
            locationGranted = locationGranted,
            storageGranted = storageGranted,
            notificationsGranted = notificationsGranted,
            allGranted = locationGranted && storageGranted && notificationsGranted
        )
    }

    /**
     * Check if a specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get list of permissions that need to be requested
     */
    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if location is enabled on device
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

/**
 * Location Service for accurate user location detection
 */
@Singleton
class LocationService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL = 10000L // 10 seconds
        private const val FASTEST_INTERVAL = 5000L // 5 seconds
    }

    data class UserLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double? = null,
        val speed: Float? = null,
        val provider: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun distanceTo(otherLat: Double, otherLon: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(latitude, longitude, otherLat, otherLon, results)
            return results[0]
        }
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /**
     * Get current location (one-shot)
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): UserLocation? {
        // Check permissions first
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                val locationRequest = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(30000) // Accept locations up to 30 seconds old
                    .build()

                fusedLocationClient.getCurrentLocation(locationRequest, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            val userLocation = UserLocation(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                altitude = if (location.hasAltitude()) location.altitude else null,
                                speed = if (location.hasSpeed()) location.speed else null,
                                provider = location.provider ?: "fused"
                            )
                            Log.d(TAG, "Location obtained: ${userLocation.latitude}, ${userLocation.longitude}")
                            continuation.resume(userLocation)
                        } else {
                            Log.w(TAG, "Location is null")
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get location: ${exception.message}")
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * Get last known location (faster, less accurate)
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): UserLocation? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(UserLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            provider = location.provider ?: "last_known"
                        ))
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    /**
     * Observe location updates as a Flow
     */
    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<UserLocation> = callbackFlow {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        provider = location.provider ?: "fused"
                    ))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Get best server based on user's actual location
     */
    suspend fun findNearestServer(
        servers: List<ServerLocation>,
        userLocation: UserLocation
    ): ServerLocation? {
        return servers.minByOrNull { server ->
            userLocation.distanceTo(server.latitude, server.longitude)
        }
    }

    data class ServerLocation(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String
    )
}
