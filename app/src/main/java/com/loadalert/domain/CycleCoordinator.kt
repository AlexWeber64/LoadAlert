package com.loadalert.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.loadalert.data.cycle.CycleStateRepository
import com.loadalert.data.history.HistoryRepository
import com.loadalert.data.model.CycleOutcome
import com.loadalert.data.model.CycleState
import com.loadalert.data.model.LaundryLocation
import com.loadalert.data.settings.SettingsRepository
import com.loadalert.location.GeofenceManager
import com.loadalert.location.LocationClient
import com.loadalert.messaging.RoommateMessenger
import com.loadalert.notifications.NotificationHelper
import com.loadalert.notifications.QuietHoursGate
import com.loadalert.widget.StartLaundryWidget
import com.loadalert.work.CycleScheduler

/**
 * The single place that decides what a laundry cycle does next.
 *
 * Workers, the geofence receiver, the notification action receiver and the UI all funnel
 * through here so the rules live in one file instead of being re-derived in four processes.
 */
class CycleCoordinator(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val cycleRepository: CycleStateRepository,
    private val historyRepository: HistoryRepository,
    private val locationClient: LocationClient,
    private val geofenceManager: GeofenceManager,
    private val notificationHelper: NotificationHelper,
    private val scheduler: CycleScheduler,
) {

    sealed interface StartResult {
        /**
         * The cycle is running. [messagingIntent] is non-null when a roommate is configured;
         * it has to be launched from an Activity, so the caller fires it.
         */
        data class Started(val cycle: CycleState, val messagingIntent: Intent?) : StartResult

        /** No saved laundry room and no usable GPS fix, so there is nothing to geofence. */
        data object NoLocation : StartResult

        data class AlreadyRunning(val cycle: CycleState) : StartResult
    }

    // region starting

    /**
     * Begins a cycle. [durationMinutes] overrides the saved default for this load only.
     *
     * The laundry room position comes from setup when it exists; otherwise we capture the
     * current fix and remember it, which is the "I'm standing there now" path taken from Home.
     */
    suspend fun startCycle(durationMinutes: Int? = null): StartResult {
        val existing = cycleRepository.current()
        if (existing.isActive) return StartResult.AlreadyRunning(existing)

        val settings = settingsRepository.current()
        val duration = (durationMinutes ?: settings.defaultDurationMinutes).coerceAtLeast(1)

        val location = settings.laundryLocation ?: captureAndRemember() ?: return StartResult.NoLocation

        val now = System.currentTimeMillis()
        val readyAt = now + duration * MILLIS_PER_MINUTE
        val cycle = CycleState(
            cycleId = now,
            startedAtEpochMs = now,
            durationMinutes = duration,
            readyAtEpochMs = readyAt,
            deadlineEpochMs = CyclePolicy.deadlineFor(now, readyAt, settings),
            location = location,
            geofenceRadiusMeters = settings.geofenceRadiusMeters,
        )
        cycleRepository.save(cycle)
        notificationHelper.createChannels()
        scheduler.scheduleWashComplete(readyAt - now)
        refreshWidget()

        val messagingIntent = settings.roommate?.let {
            RoommateMessenger(appContext).buildStartMessageIntent(it, duration)
        }
        Log.i(TAG, "Cycle ${cycle.cycleId} started, ready in $duration min")
        return StartResult.Started(cycle, messagingIntent)
    }

    private suspend fun captureAndRemember(): LaundryLocation? =
        locationClient.captureLaundryLocation()?.also { settingsRepository.setLaundryLocation(it) }

    // endregion

    // region timer and reminder checks

    /** Runs when the wash should be finished. Registers the fence, then decides whether to nag. */
    suspend fun onWashComplete() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return
        val location = cycle.location ?: run {
            finish(cycle, CycleOutcome.CANCELLED)
            return
        }

        val geofenced = geofenceManager.register(location, cycle.geofenceRadiusMeters)
        if (!geofenced) {
            Log.i(TAG, "Geofence unavailable; falling back to one-shot location checks")
        }

        if (isAtLaundryRoom(cycle)) {
            // They are already standing there, so saying anything would just be noise.
            finish(cycle, CycleOutcome.ALREADY_THERE)
            return
        }
        remindOrQueue(cycle)
    }

    /** Runs on the reminder interval until something ends the cycle. */
    suspend fun onReminderCheck() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return

        if (System.currentTimeMillis() >= cycle.deadlineEpochMs) {
            finish(cycle, CycleOutcome.TIMED_OUT)
            notificationHelper.showCycleEnded(
                title = "LoadAlert stopped reminding you",
                body = "This cycle hit its maximum length. Your laundry may still be in the machine.",
            )
            return
        }

        if (isAtLaundryRoom(cycle)) {
            finish(cycle, CycleOutcome.CONFIRMED_COLLECTED)
            return
        }

        if (cycle.arrivedAfterReady) {
            // The fence saw them arrive; wait for the exit rather than nagging someone
            // who is standing in front of the machine.
            scheduler.scheduleReminderCheck(remainingUntilDeadline(cycle))
            return
        }

        remindOrQueue(cycle)
    }

    /**
     * Posts a reminder, or defers it to the end of quiet hours.
     *
     * A reminder suppressed by quiet hours does not count as ignored, so the escalation ladder
     * does not climb while the user is asleep.
     */
    private suspend fun remindOrQueue(cycle: CycleState) {
        val settings = settingsRepository.current()
        val now = System.currentTimeMillis()

        if (QuietHoursGate.isQuiet(settings.quietHours, now)) {
            val resumeAt = QuietHoursGate.endOfQuietPeriod(settings.quietHours, now)
            cycleRepository.update { it.copy(reminderQueued = true) }
            scheduleNext(cycle, (resumeAt - now).coerceAtLeast(MILLIS_PER_MINUTE))
            Log.i(TAG, "Reminder held for quiet hours until $resumeAt")
            return
        }

        val urgent = CyclePolicy.isUrgent(settings, cycle.ignoredChecks)
        val posted = notificationHelper.showLaundryReady(cycle, urgent)

        val updated = cycleRepository.update {
            it.copy(
                ignoredChecks = if (posted) it.ignoredChecks + 1 else it.ignoredChecks,
                reminderQueued = false,
            )
        } ?: return

        scheduleNext(updated, CyclePolicy.intervalMillis(settings, updated.ignoredChecks))
    }

    private fun remainingUntilDeadline(cycle: CycleState): Long =
        CyclePolicy.remainingUntilDeadline(cycle.deadlineEpochMs, System.currentTimeMillis())

    private fun scheduleNext(cycle: CycleState, delayMs: Long) {
        scheduler.scheduleReminderCheck(
            CyclePolicy.nextCheckDelay(delayMs, cycle.deadlineEpochMs, System.currentTimeMillis()),
        )
    }

    /**
     * One-shot position check. This backstops the Geofencing API, which can take minutes to
     * report a transition and is unreliable over the short span of a single cycle.
     * An unknown position counts as "not there", so we remind rather than go quiet.
     */
    private suspend fun isAtLaundryRoom(cycle: CycleState): Boolean {
        val target = cycle.location ?: return false
        val fix = locationClient.currentLocation() ?: return false
        return LocationClient.distanceMeters(fix, target) <= cycle.geofenceRadiusMeters
    }

    // endregion

    // region geofence transitions

    suspend fun onGeofenceEnter() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive || !cycle.isReady) return
        Log.i(TAG, "Arrived at laundry room during cycle ${cycle.cycleId}")
        notificationHelper.cancelReminder()
        val updated = cycleRepository.update { it.copy(arrivedAfterReady = true) } ?: return
        // Stop nagging, but keep one check on the books so the cycle cannot hang forever.
        scheduler.scheduleReminderCheck(remainingUntilDeadline(updated))
        refreshWidget()
    }

    suspend fun onGeofenceExit() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return
        // Leaving only means anything if we saw them arrive after the wash finished.
        if (!cycle.arrivedAfterReady) return
        Log.i(TAG, "Left laundry room after arriving; treating cycle ${cycle.cycleId} as collected")
        finish(cycle, CycleOutcome.CONFIRMED_COLLECTED)
    }

    // endregion

    // region user actions

    suspend fun markCollected() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return
        finish(cycle, CycleOutcome.ACKNOWLEDGED)
    }

    suspend fun cancelCycle() {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return
        finish(cycle, CycleOutcome.CANCELLED)
    }

    /** Pushes the next reminder out without counting the current one as ignored. */
    suspend fun snooze(minutes: Int = NotificationHelper.SNOOZE_MINUTES) {
        val cycle = cycleRepository.current()
        if (!cycle.isActive) return
        notificationHelper.cancelReminder()
        val updated = cycleRepository.update {
            it.copy(ignoredChecks = (it.ignoredChecks - 1).coerceAtLeast(0))
        } ?: return
        scheduleNext(updated, minutes * MILLIS_PER_MINUTE)
    }

    // endregion

    /**
     * Tears down everything this cycle owns and writes it to history.
     *
     * Two things here are load-bearing, both because finish() is usually reached from inside
     * one of our own workers:
     *
     * 1. NonCancellable. [CycleScheduler.cancelAll] cancels the unique work that the calling
     *    worker *is*, which cancels that worker's coroutine. Without this, the cancellation
     *    tears down the rest of this function mid-flight and the cycle is left marked active
     *    forever with nothing scheduled to move it along.
     * 2. Order. Durable state is written before WorkManager is touched, so even an unexpected
     *    cancellation cannot lose the history entry or strand the cycle.
     */
    private suspend fun finish(cycle: CycleState, outcome: CycleOutcome) = withContext(NonCancellable) {
        historyRepository.record(cycle, outcome, System.currentTimeMillis())
        cycleRepository.clear()
        notificationHelper.cancelReminder()
        geofenceManager.unregister()
        scheduler.cancelAll()
        refreshWidget()
        Log.i(TAG, "Cycle ${cycle.cycleId} finished: $outcome")
    }

    private suspend fun refreshWidget() {
        StartLaundryWidget.refresh(appContext)
    }

    companion object {
        private const val TAG = "CycleCoordinator"
        private const val MILLIS_PER_MINUTE = CyclePolicy.MILLIS_PER_MINUTE
    }
}
