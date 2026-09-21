package com.loadalert.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.loadalert.data.model.LaundryLocation
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thin wrapper over [com.google.android.gms.location.FusedLocationProviderClient].
 *
 * Used for the two moments the Geofencing API cannot cover: capturing the laundry room
 * position at setup/start, and the one-shot "are they there right now?" check that backstops
 * geofence transitions on short cycles.
 */
class LocationClient(private val context: Context) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Returns a fresh fix, falling back to the last known position if a fresh one does not
     * arrive in time. Null means we have no permission or no position at all.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMs: Long = FRESH_FIX_TIMEOUT_MS): Location? {
        if (!LocationPermissions.hasForegroundLocation(context)) return null
        val cancellation = CancellationTokenSource()
        val fresh = try {
            withTimeoutOrNull(timeoutMs) {
                fusedClient.getCurrentLocation(
                    if (LocationPermissions.hasPreciseLocation(context)) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    },
                    cancellation.token,
                ).await()
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
        if (fresh != null) return fresh

        return try {
            fusedClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Captures the device's current position as a saveable laundry room pin. */
    suspend fun captureLaundryLocation(label: String = "Laundry room"): LaundryLocation? =
        currentLocation()?.let { LaundryLocation(it.latitude, it.longitude, label) }

    companion object {
        private const val FRESH_FIX_TIMEOUT_MS = 20_000L

        /** Metres between a device fix and the saved laundry room. */
        fun distanceMeters(from: Location, to: LaundryLocation): Float {
            val results = FloatArray(1)
            Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
            return results[0]
        }
    }
}
