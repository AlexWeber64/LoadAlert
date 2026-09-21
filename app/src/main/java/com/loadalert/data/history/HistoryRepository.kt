package com.loadalert.data.history

import com.loadalert.data.model.CycleOutcome
import com.loadalert.data.model.CycleState
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: CycleHistoryDao) {

    val history: Flow<List<CycleRecord>> = dao.observeAll()

    suspend fun record(cycle: CycleState, outcome: CycleOutcome, endedAtEpochMs: Long) {
        dao.upsert(
            CycleRecord(
                cycleId = cycle.cycleId,
                startedAtEpochMs = cycle.startedAtEpochMs,
                durationMinutes = cycle.durationMinutes,
                readyAtEpochMs = cycle.readyAtEpochMs,
                endedAtEpochMs = endedAtEpochMs,
                outcome = outcome,
                remindersSent = cycle.ignoredChecks,
                locationLabel = cycle.location?.label ?: "Laundry room",
            ),
        )
    }

    suspend fun clear() = dao.clear()
}
