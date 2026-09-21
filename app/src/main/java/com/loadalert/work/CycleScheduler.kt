package com.loadalert.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns every deferred job the cycle needs.
 *
 * Reminder checks are a chain of one-shot jobs rather than a PeriodicWorkRequest: WorkManager
 * clamps periodic work to a 15 minute floor, and the default reminder interval is 5 minutes.
 * Each check schedules the next one, so the interval stays honest and escalation can shorten
 * it mid-cycle.
 */
class CycleScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Fires once when the wash should be finished. */
    fun scheduleWashComplete(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<LaundryReadyWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_CYCLE)
            .build()
        workManager.enqueueUniqueWork(WORK_WASH_COMPLETE, ExistingWorkPolicy.REPLACE, request)
    }

    /** Queues the next "are they there yet?" check. */
    fun scheduleReminderCheck(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_CYCLE)
            .build()
        workManager.enqueueUniqueWork(WORK_REMINDER_CHECK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WORK_WASH_COMPLETE)
        workManager.cancelUniqueWork(WORK_REMINDER_CHECK)
    }

    companion object {
        const val WORK_WASH_COMPLETE = "loadalert_wash_complete"
        const val WORK_REMINDER_CHECK = "loadalert_reminder_check"
        const val TAG_CYCLE = "loadalert_cycle"
    }
}
