package com.loadalert.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.loadalert.di.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the notification action buttons. */
class CycleActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val coordinator = appContext.container.cycleCoordinator

        // Broadcast receivers are killed the moment onReceive returns, so hold the process
        // open until the suspend work is done.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_MARK_COLLECTED -> coordinator.markCollected()
                    ACTION_SNOOZE -> coordinator.snooze()
                    else -> Log.w(TAG, "Unhandled action $action")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CycleActionReceiver"
        const val ACTION_MARK_COLLECTED = "com.loadalert.action.MARK_COLLECTED"
        const val ACTION_SNOOZE = "com.loadalert.action.SNOOZE"
        const val EXTRA_CYCLE_ID = "com.loadalert.extra.CYCLE_ID"
    }
}
