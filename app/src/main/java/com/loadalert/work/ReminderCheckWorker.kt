package com.loadalert.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.cancellation.CancellationException
import com.loadalert.di.container

/**
 * One "are they there yet?" check. Each run queues the next one through the coordinator,
 * so the chain ends naturally when the cycle does.
 */
class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.container.cycleCoordinator.onReminderCheck()
        Result.success()
    } catch (e: CancellationException) {
        // Ending a cycle cancels this worker's own unique work. That is a normal shutdown,
        // not a failure to retry.
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Reminder check failed", e)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "ReminderCheckWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
