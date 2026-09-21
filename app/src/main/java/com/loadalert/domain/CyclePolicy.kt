package com.loadalert.domain

import com.loadalert.data.model.LaundrySettings

/**
 * The arithmetic behind a cycle: when to give up, how long until the next check, and whether
 * we have reached the point of shouting.
 *
 * Kept free of Android types and of the clock so it can be tested directly. [CycleCoordinator]
 * owns the side effects; this owns the decisions.
 */
object CyclePolicy {

    const val MILLIS_PER_MINUTE = 60_000L

    /** Even a very long wash gets at least this much reminder time before we give up. */
    const val MIN_REMINDER_WINDOW_MS = 30 * MILLIS_PER_MINUTE

    /**
     * When the cycle stops reminding.
     *
     * Measured from the start of the cycle, because that is what "max cycle time" means to the
     * user. The floor stops a pathological config — a 2 hour wash with a 1 hour cap — from
     * producing a deadline that has already passed by the time the wash finishes.
     */
    fun deadlineFor(startedAtEpochMs: Long, readyAtEpochMs: Long, settings: LaundrySettings): Long =
        maxOf(
            startedAtEpochMs + settings.maxCycleMinutes * MILLIS_PER_MINUTE,
            readyAtEpochMs + MIN_REMINDER_WINDOW_MS,
        )

    /** True once enough checks have gone unanswered to warrant the louder channel. */
    fun shouldEscalate(settings: LaundrySettings, ignoredChecks: Int): Boolean =
        ignoredChecks >= settings.escalation.escalateAfterIgnoredChecks

    /** Whether this reminder should use the urgent channel. */
    fun isUrgent(settings: LaundrySettings, ignoredChecks: Int): Boolean =
        settings.escalation.increaseUrgency && shouldEscalate(settings, ignoredChecks)

    /**
     * Gap until the next check. Escalation may shorten it, but never below one minute —
     * WorkManager would not honour a tighter schedule anyway.
     */
    fun intervalMillis(settings: LaundrySettings, ignoredChecks: Int): Long {
        val escalated = shouldEscalate(settings, ignoredChecks) && settings.escalation.shortenInterval
        val minutes = if (escalated) {
            settings.escalation.escalatedIntervalMinutes
        } else {
            settings.reminderIntervalMinutes
        }
        return minutes.coerceAtLeast(1) * MILLIS_PER_MINUTE
    }

    /** Time left before the cycle must stop, floored so we never schedule a zero delay. */
    fun remainingUntilDeadline(deadlineEpochMs: Long, nowEpochMs: Long): Long =
        (deadlineEpochMs - nowEpochMs).coerceAtLeast(MILLIS_PER_MINUTE)

    /** A requested delay, clamped so no check is ever scheduled past the deadline. */
    fun nextCheckDelay(requestedDelayMs: Long, deadlineEpochMs: Long, nowEpochMs: Long): Long =
        requestedDelayMs.coerceAtMost(remainingUntilDeadline(deadlineEpochMs, nowEpochMs))
}
