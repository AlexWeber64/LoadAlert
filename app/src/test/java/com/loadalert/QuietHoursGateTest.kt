package com.loadalert

import com.loadalert.data.model.QuietHours
import com.loadalert.notifications.QuietHoursGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class QuietHoursGateTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 3, 14, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `disabled quiet hours never suppress`() {
        val quiet = QuietHours(enabled = false, startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)
        assertFalse(QuietHoursGate.isQuiet(quiet, at(23), zone))
    }

    @Test
    fun `overnight window covers both sides of midnight`() {
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)
        assertTrue(QuietHoursGate.isQuiet(quiet, at(23), zone))
        assertTrue(QuietHoursGate.isQuiet(quiet, at(3), zone))
        assertFalse(QuietHoursGate.isQuiet(quiet, at(12), zone))
        assertFalse(QuietHoursGate.isQuiet(quiet, at(7), zone))
    }

    @Test
    fun `same day window is half open`() {
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 9 * 60, endMinuteOfDay = 17 * 60)
        assertTrue(QuietHoursGate.isQuiet(quiet, at(9), zone))
        assertTrue(QuietHoursGate.isQuiet(quiet, at(16, 59), zone))
        assertFalse(QuietHoursGate.isQuiet(quiet, at(17), zone))
        assertFalse(QuietHoursGate.isQuiet(quiet, at(8, 59), zone))
    }

    @Test
    fun `end of quiet period rolls to tomorrow when already past today`() {
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)
        // 23:00 is before tomorrow's 07:00 end.
        assertEquals(
            LocalDateTime.of(2026, 3, 15, 7, 0).atZone(zone).toInstant().toEpochMilli(),
            QuietHoursGate.endOfQuietPeriod(quiet, at(23), zone),
        )
        // 03:00 is before the same day's 07:00 end.
        assertEquals(
            LocalDateTime.of(2026, 3, 14, 7, 0).atZone(zone).toInstant().toEpochMilli(),
            QuietHoursGate.endOfQuietPeriod(quiet, at(3), zone),
        )
    }
}
