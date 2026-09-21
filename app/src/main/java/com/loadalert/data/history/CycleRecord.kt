package com.loadalert.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.loadalert.data.model.CycleOutcome

@Entity(tableName = "cycle_history")
data class CycleRecord(
    @PrimaryKey val cycleId: Long,
    val startedAtEpochMs: Long,
    val durationMinutes: Int,
    val readyAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val outcome: CycleOutcome,
    val remindersSent: Int,
    val locationLabel: String,
) {
    /** How long the load sat in the machine after it finished. */
    val pickupDelayMs: Long get() = (endedAtEpochMs - readyAtEpochMs).coerceAtLeast(0L)

    /**
     * "On time" means the cycle ended with a real confirmation and the load was not left
     * sitting longer than [ON_TIME_GRACE_MS] after the wash finished.
     */
    val pickedUpOnTime: Boolean
        get() = when (outcome) {
            CycleOutcome.ALREADY_THERE -> true
            CycleOutcome.ACKNOWLEDGED,
            CycleOutcome.CONFIRMED_COLLECTED,
            -> pickupDelayMs <= ON_TIME_GRACE_MS
            CycleOutcome.TIMED_OUT, CycleOutcome.CANCELLED, CycleOutcome.IN_PROGRESS -> false
        }

    companion object {
        const val ON_TIME_GRACE_MS = 10 * 60 * 1000L
    }
}
