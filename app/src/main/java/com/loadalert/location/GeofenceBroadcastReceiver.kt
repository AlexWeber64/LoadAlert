package com.loadalert.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.loadalert.di.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives enter/exit transitions for the laundry-room fence. */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            Log.w(TAG, "Geofence event error: " + (event?.errorCode ?: "null event"))
            return
        }

        val transition = event.geofenceTransition
        val appContext = context.applicationContext
        val coordinator = appContext.container.cycleCoordinator

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (transition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> coordinator.onGeofenceEnter()
                    Geofence.GEOFENCE_TRANSITION_EXIT -> coordinator.onGeofenceExit()
                    else -> Log.d(TAG, "Ignoring transition $transition")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle geofence transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val ACTION_GEOFENCE_EVENT = "com.loadalert.action.GEOFENCE_EVENT"
    }
}
