package com.loadalert

import com.loadalert.data.history.CycleRecord
import com.loadalert.data.model.CycleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleRecordTest {

    private val readyAt = 1_000_000L

    private fun record(outcome: CycleOutcome, endedAt: Long) = CycleRecord(
        cycleId = 1L,
        startedAtEpochMs = readyAt - 45 * 60_000L,
        durationMinutes = 45,
        readyAtEpochMs = readyAt,
        endedAtEpochMs = endedAt,
        outcome = outcome,
        remindersSent = 0,
        locationLabel = "Basement",
    )

    @Test
    fun `already there always counts as on time`() {
        assertTrue(record(CycleOutcome.ALREADY_THERE, readyAt + 2 * 60 * 60_000L).pickedUpOnTime)
    }

    @Test
    fun `prompt acknowledgement is on time`() {
        assertTrue(record(CycleOutcome.ACKNOWLEDGED, readyAt + 5 * 60_000L).pickedUpOnTime)
    }

    @Test
    fun `slow pick up is not on time`() {
        assertFalse(record(CycleOutcome.CONFIRMED_COLLECTED, readyAt + 40 * 60_000L).pickedUpOnTime)
    }

    @Test
    fun `timeout and cancellation are never on time`() {
        assertFalse(record(CycleOutcome.TIMED_OUT, readyAt + 60_000L).pickedUpOnTime)
        assertFalse(record(CycleOutcome.CANCELLED, readyAt + 60_000L).pickedUpOnTime)
    }

    @Test
    fun `pick up delay never goes negative`() {
        assertEquals(0L, record(CycleOutcome.ACKNOWLEDGED, readyAt - 60_000L).pickupDelayMs)
    }
}
