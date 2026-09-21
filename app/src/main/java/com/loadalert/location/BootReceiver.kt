package com.loadalert.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.loadalert.di.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers the laundry-room geofence after a reboot.
 *
 * WorkManager restores its own jobs, so the timer and reminder chain come back by themselves.
 * Geofences do not: the platform drops every registration on restart, and without this the
 * leave-after-arriving confirmation would silently stop working for any cycle that spans a
 * reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val container = appContext.container
                val cycle = container.cycleRepository.current()
                val location = cycle.location
                // Only cycles that have already finished washing have a fence to restore.
                if (cycle.isActive && cycle.isReady && location != null) {
                    container.geofenceManager.register(location, cycle.geofenceRadiusMeters)
                    Log.i(TAG, "Restored geofence for cycle ${cycle.cycleId} after boot")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore geofence after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
