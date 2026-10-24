package com.loadalert

import com.loadalert.data.model.CycleState
import com.loadalert.data.model.LaundrySettings
import com.loadalert.data.model.coerceDurationMinutes
import com.loadalert.data.model.sanitizeWasherLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Validation for the two hand-typed inputs: a custom wash length and a washer label. */
class CycleInputTest {

    @Test
    fun `a custom duration inside the range is kept exactly`() {
        assertEquals(37, coerceDurationMinutes(37))
        assertEquals(1, coerceDurationMinutes(1))
        assertEquals(600, coerceDurationMinutes(600))
    }

    @Test
    fun `a custom duration outside the range is clamped, never rejected`() {
        assertEquals(LaundrySettings.MIN_DURATION_MINUTES, coerceDurationMinutes(0))
        assertEquals(LaundrySettings.MIN_DURATION_MINUTES, coerceDurationMinutes(-90))
        assertEquals(LaundrySettings.MAX_DURATION_MINUTES, coerceDurationMinutes(5_000))
    }

    @Test
    fun `washer labels are normalised rather than rejected`() {
        assertEquals("3", sanitizeWasherLabel(" 3 "))
        assertEquals("3", sanitizeWasherLabel("#3"))
        assertEquals("B2", sanitizeWasherLabel("b2"))
    }

    @Test
    fun `an unusable washer label collapses to blank`() {
        assertEquals("", sanitizeWasherLabel("   "))
        assertEquals("", sanitizeWasherLabel("---"))
    }

    @Test
    fun `washer labels are capped so they stay readable in a message`() {
        assertEquals(
            LaundrySettings.MAX_WASHER_LABEL_LENGTH,
            sanitizeWasherLabel("123456789").length,
        )
    }

    /**
     * The mid-cycle message rounds up: a machine with 30 seconds left is still running, so
     * "ready in 0 min" would be wrong.
     */
    @Test
    fun `minutes remaining rounds up while the wash is still running`() {
        val readyAt = 1_000_000L
        val cycle = CycleState(cycleId = 1L, readyAtEpochMs = readyAt)

        assertEquals(5, cycle.minutesRemaining(readyAt - 5 * 60_000L))
        assertEquals(2, cycle.minutesRemaining(readyAt - 61_000L))
        assertEquals(1, cycle.minutesRemaining(readyAt - 30_000L))
        assertEquals(1, cycle.minutesRemaining(readyAt - 1L))
    }

    @Test
    fun `minutes remaining is zero once the wash is finished`() {
        val readyAt = 1_000_000L
        val cycle = CycleState(cycleId = 1L, readyAtEpochMs = readyAt)

        assertEquals(0, cycle.minutesRemaining(readyAt))
        assertEquals(0, cycle.minutesRemaining(readyAt + 90_000L))
    }
}
