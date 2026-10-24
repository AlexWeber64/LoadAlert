package com.loadalert

import com.loadalert.messaging.RoommateMessenger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wording of the two roommate messages. Pure strings, no Android types, so the exact text
 * the roommate receives is pinned without an emulator.
 */
class RoommateMessageTest {

    @Test
    fun `start message without a washer is unchanged`() {
        assertEquals(
            "Started a load, ready in 45 min",
            RoommateMessenger.startMessage(45),
        )
    }

    @Test
    fun `start message names the washer when one is tracked`() {
        assertEquals(
            "Started a load in washer 3, ready in 45 min",
            RoommateMessenger.startMessage(45, "3"),
        )
    }

    @Test
    fun `blank washer label is treated as not tracked`() {
        assertEquals(
            "Started a load, ready in 20 min",
            RoommateMessenger.startMessage(20, "   "),
        )
    }

    @Test
    fun `update message reports the time left, not the original duration`() {
        assertEquals(
            "Laundry update: still washing, ready in 12 min",
            RoommateMessenger.updateMessage(12),
        )
    }

    @Test
    fun `update message names the washer when one is tracked`() {
        assertEquals(
            "Laundry update: still washing in washer B2, ready in 7 min",
            RoommateMessenger.updateMessage(7, "B2"),
        )
    }

    @Test
    fun `update message switches wording once the wash is done`() {
        assertEquals("Laundry update: the load is done", RoommateMessenger.updateMessage(0))
        assertEquals(
            "Laundry update: the load in washer 5 is done",
            RoommateMessenger.updateMessage(0, "5"),
        )
    }

    /** A negative remaining time can only mean the wash is over; it must not leak into the text. */
    @Test
    fun `negative remaining minutes read as done`() {
        assertEquals("Laundry update: the load is done", RoommateMessenger.updateMessage(-3))
    }
}
