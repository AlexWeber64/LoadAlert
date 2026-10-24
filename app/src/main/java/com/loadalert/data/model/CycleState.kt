package com.loadalert.data.model

/** Why a laundry cycle stopped. Recorded in history. */
enum class CycleOutcome {
    /** Still running. */
    IN_PROGRESS,

    /** The timer finished and the user was already standing at the laundry room. */
    ALREADY_THERE,

    /** The user tapped "I got it" on a reminder. */
    ACKNOWLEDGED,

    /** Geofence saw the user arrive after the wash finished and then leave again. */
    CONFIRMED_COLLECTED,

    /** We hit the max cycle timeout without confirmation. */
    TIMED_OUT,

    /** The user cancelled from the app. */
    CANCELLED,
}

/**
 * The single in-flight laundry cycle. Persisted so that workers and broadcast receivers,
 * which run without the UI process being alive, all read the same state.
 */
data class CycleState(
    val cycleId: Long = NO_CYCLE,
    val startedAtEpochMs: Long = 0L,
    val durationMinutes: Int = 0,
    val readyAtEpochMs: Long = 0L,
    val deadlineEpochMs: Long = 0L,
    val location: LaundryLocation? = null,
    val geofenceRadiusMeters: Float = 100f,
    /** How many reminder checks have fired without the user acknowledging. */
    val ignoredChecks: Int = 0,
    /** Geofence has reported the user entering the laundry room since the wash finished. */
    val arrivedAfterReady: Boolean = false,
    /** A reminder was suppressed by quiet hours and should fire once the window closes. */
    val reminderQueued: Boolean = false,
    /** Which machine this load is in, or blank when the user is not tracking that. */
    val washerLabel: String = "",
) {
    val isActive: Boolean get() = cycleId != NO_CYCLE

    val isReady: Boolean get() = isActive && System.currentTimeMillis() >= readyAtEpochMs

    fun millisRemaining(now: Long = System.currentTimeMillis()): Long =
        (readyAtEpochMs - now).coerceAtLeast(0L)

    /**
     * Whole minutes left on the wash, rounded up. Telling a roommate "ready in 0 min" while the
     * machine is still running would be wrong, so any part-minute counts as one.
     */
    fun minutesRemaining(now: Long = System.currentTimeMillis()): Int {
        val millis = millisRemaining(now)
        return if (millis == 0L) 0 else ((millis + 59_999L) / 60_000L).toInt()
    }

    companion object {
        const val NO_CYCLE = 0L
        val NONE = CycleState()
    }
}
