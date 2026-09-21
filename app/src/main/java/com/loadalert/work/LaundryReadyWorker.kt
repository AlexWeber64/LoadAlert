package com.loadalert.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.cancellation.CancellationException
import com.loadalert.di.container

/** Fires once, when the wash should be done. Hands straight off to the coordinator. */
class LaundryReadyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.container.cycleCoordinator.onWashComplete()
        Result.success()
    } catch (e: CancellationException) {
        // Ending a cycle cancels this worker's own unique work. That is a normal shutdown,
        // not a failure to retry.
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Wash-complete handling failed", e)
        // Retrying matters here: missing this is the whole point of the app.
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "LaundryReadyWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
