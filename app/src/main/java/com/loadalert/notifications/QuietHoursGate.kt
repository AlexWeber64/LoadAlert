package com.loadalert.notifications

import com.loadalert.data.model.QuietHours
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Decides whether a reminder may make noise right now, and when the next allowed moment is. */
object QuietHoursGate {

    private const val MINUTES_PER_DAY = 24 * 60

    fun isQuiet(
        quietHours: QuietHours,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!quietHours.enabled) return false
        if (quietHours.startMinuteOfDay == quietHours.endMinuteOfDay) return false
        val minuteOfDay = zonedNow(nowEpochMs, zone).let { it.hour * 60 + it.minute }
        return contains(quietHours, minuteOfDay)
    }

    /**
     * Epoch millis at which the quiet window closes. Callers reschedule the suppressed reminder
     * for this moment rather than dropping it, so a load started late at night is still
     * surfaced in the morning.
     */
    fun endOfQuietPeriod(
        quietHours: QuietHours,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = zonedNow(nowEpochMs, zone)
        val endToday = now.toLocalDate().atStartOfDay(zone)
            .plusMinutes(quietHours.endMinuteOfDay.toLong())
        val end = if (endToday.toInstant().toEpochMilli() <= nowEpochMs) {
            endToday.plusDays(1)
        } else {
            endToday
        }
        return end.toInstant().toEpochMilli()
    }

    private fun contains(quietHours: QuietHours, minuteOfDay: Int): Boolean {
        val start = quietHours.startMinuteOfDay.mod(MINUTES_PER_DAY)
        val end = quietHours.endMinuteOfDay.mod(MINUTES_PER_DAY)
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            // Window wraps past midnight, for example 22:00 through 07:00.
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun zonedNow(nowEpochMs: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(nowEpochMs).atZone(zone)
}
