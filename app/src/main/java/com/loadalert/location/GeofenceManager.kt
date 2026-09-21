package com.loadalert.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.loadalert.data.model.LaundryLocation
import kotlinx.coroutines.tasks.await

/**
 * Owns the single laundry-room geofence.
 *
 * The geofence is registered when the wash finishes, not when it starts: before then we do not
 * care where the user is, and a long-lived fence would burn battery for nothing.
 */
class GeofenceManager(private val context: Context) {

    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceBroadcastReceiver::class.java)
                .setAction(GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Registers an enter/exit fence around [location]. Returns false when the platform refuses,
     * which is the signal for callers to lean on one-shot location checks instead.
     */
    @SuppressLint("MissingPermission")
    suspend fun register(location: LaundryLocation, radiusMeters: Float): Boolean {
        if (!LocationPermissions.hasBackgroundLocation(context)) {
            Log.i(TAG, "Skipping geofence registration: no background location permission")
            return false
        }
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(location.latitude, location.longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            // INITIAL_TRIGGER_ENTER makes the fence fire immediately if the user is already
            // inside when we register, which is exactly the "they're already there" case.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return try {
            geofencingClient.addGeofences(request, pendingIntent).await()
            Log.i(TAG, "Geofence registered at ${location.label} r=${radiusMeters}m")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Geofence registration denied", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Geofence registration failed", e)
            false
        }
    }

    suspend fun unregister() {
        try {
            geofencingClient.removeGeofences(listOf(GEOFENCE_ID)).await()
        } catch (e: Exception) {
            Log.w(TAG, "Geofence removal failed", e)
        }
    }

    companion object {
        const val GEOFENCE_ID = "loadalert_laundry_room"
        private const val REQUEST_CODE = 4101
        private const val LOITERING_DELAY_MS = 60_000
        private const val TAG = "GeofenceManager"
    }
}
