package com.loadalert

import com.loadalert.data.model.EscalationSettings
import com.loadalert.data.model.LaundrySettings
import com.loadalert.domain.CyclePolicy
import com.loadalert.domain.CyclePolicy.MILLIS_PER_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclePolicyTest {

    private val start = 1_000_000_000L

    private fun settings(
        maxCycleMinutes: Int = 180,
        reminderIntervalMinutes: Int = 5,
        escalation: EscalationSettings = EscalationSettings(),
    ) = LaundrySettings(
        maxCycleMinutes = maxCycleMinutes,
        reminderIntervalMinutes = reminderIntervalMinutes,
        escalation = escalation,
    )

    // region deadline

    @Test
    fun `deadline is max cycle time measured from the start of the cycle`() {
        val readyAt = start + 45 * MILLIS_PER_MINUTE
        assertEquals(
            start + 180 * MILLIS_PER_MINUTE,
            CyclePolicy.deadlineFor(start, readyAt, settings(maxCycleMinutes = 180)),
        )
    }

    @Test
    fun `a wash longer than the cap still gets a reminder window`() {
        // 2 hour wash, 1 hour cap: measuring from the start alone would put the deadline
        // an hour before the wash even finishes.
        val readyAt = start + 120 * MILLIS_PER_MINUTE
        val deadline = CyclePolicy.deadlineFor(start, readyAt, settings(maxCycleMinutes = 60))

        assertTrue("deadline must fall after the wash finishes", deadline > readyAt)
        assertEquals(readyAt + CyclePolicy.MIN_REMINDER_WINDOW_MS, deadline)
    }

    @Test
    fun `the floor does not shorten a generous cap`() {
        val readyAt = start + 30 * MILLIS_PER_MINUTE
        assertEquals(
            start + 360 * MILLIS_PER_MINUTE,
            CyclePolicy.deadlineFor(start, readyAt, settings(maxCycleMinutes = 360)),
        )
    }

    // endregion

    // region escalation

    @Test
    fun `escalation kicks in only at the configured number of ignored checks`() {
        val s = settings(escalation = EscalationSettings(escalateAfterIgnoredChecks = 3))
        assertFalse(CyclePolicy.shouldEscalate(s, ignoredChecks = 2))
        assertTrue(CyclePolicy.shouldEscalate(s, ignoredChecks = 3))
        assertTrue(CyclePolicy.shouldEscalate(s, ignoredChecks = 9))
    }

    @Test
    fun `urgency can be turned off independently of escalation`() {
        val s = settings(
            escalation = EscalationSettings(escalateAfterIgnoredChecks = 2, increaseUrgency = false),
        )
        assertTrue(CyclePolicy.shouldEscalate(s, ignoredChecks = 5))
        assertFalse("urgency disabled must not use the loud channel", CyclePolicy.isUrgent(s, 5))
    }

    @Test
    fun `interval shortens once escalated`() {
        val s = settings(
            reminderIntervalMinutes = 10,
            escalation = EscalationSettings(
                escalateAfterIgnoredChecks = 2,
                shortenInterval = true,
                escalatedIntervalMinutes = 2,
            ),
        )
        assertEquals(10 * MILLIS_PER_MINUTE, CyclePolicy.intervalMillis(s, ignoredChecks = 1))
        assertEquals(2 * MILLIS_PER_MINUTE, CyclePolicy.intervalMillis(s, ignoredChecks = 2))
    }

    @Test
    fun `interval stays put when shortening is disabled`() {
        val s = settings(
            reminderIntervalMinutes = 10,
            escalation = EscalationSettings(escalateAfterIgnoredChecks = 2, shortenInterval = false),
        )
        assertEquals(10 * MILLIS_PER_MINUTE, CyclePolicy.intervalMillis(s, ignoredChecks = 5))
    }

    @Test
    fun `interval never drops below a minute`() {
        val s = settings(reminderIntervalMinutes = 0)
        assertEquals(MILLIS_PER_MINUTE, CyclePolicy.intervalMillis(s, ignoredChecks = 0))
    }

    // endregion

    // region scheduling

    @Test
    fun `remaining time is floored rather than going negative`() {
        val deadline = start + 10 * MILLIS_PER_MINUTE
        assertEquals(
            MILLIS_PER_MINUTE,
            CyclePolicy.remainingUntilDeadline(deadline, nowEpochMs = deadline + 5 * MILLIS_PER_MINUTE),
        )
    }

    @Test
    fun `a check is never scheduled past the deadline`() {
        val now = start
        val deadline = start + 3 * MILLIS_PER_MINUTE
        // Quiet hours could ask for an 8 hour delay; the deadline wins.
        assertEquals(
            3 * MILLIS_PER_MINUTE,
            CyclePolicy.nextCheckDelay(8 * 60 * MILLIS_PER_MINUTE, deadline, now),
        )
    }

    @Test
    fun `a check inside the deadline is left alone`() {
        val now = start
        val deadline = start + 60 * MILLIS_PER_MINUTE
        assertEquals(
            5 * MILLIS_PER_MINUTE,
            CyclePolicy.nextCheckDelay(5 * MILLIS_PER_MINUTE, deadline, now),
        )
    }

    // endregion
}
