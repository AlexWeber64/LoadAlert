package com.loadalert.data.cycle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.loadalert.data.model.CycleState
import com.loadalert.data.model.LaundryLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cycleDataStore: DataStore<Preferences> by preferencesDataStore(name = "loadalert_cycle")

/**
 * Stores the single in-flight cycle. Workers and receivers run in short-lived processes,
 * so every mutation goes straight to disk rather than living in memory.
 */
class CycleStateRepository(private val context: Context) {

    private object Keys {
        val CYCLE_ID = longPreferencesKey("cycle_id")
        val STARTED_AT = longPreferencesKey("started_at")
        val DURATION = intPreferencesKey("duration_minutes")
        val READY_AT = longPreferencesKey("ready_at")
        val DEADLINE = longPreferencesKey("deadline")
        val LAT = doublePreferencesKey("lat")
        val LNG = doublePreferencesKey("lng")
        val LABEL = stringPreferencesKey("label")
        val RADIUS = floatPreferencesKey("radius")
        val IGNORED_CHECKS = intPreferencesKey("ignored_checks")
        val ARRIVED_AFTER_READY = booleanPreferencesKey("arrived_after_ready")
        val REMINDER_QUEUED = booleanPreferencesKey("reminder_queued")
        val WASHER_LABEL = stringPreferencesKey("washer_label")
    }

    val state: Flow<CycleState> = context.cycleDataStore.data.map { prefs ->
        val id = prefs[Keys.CYCLE_ID] ?: CycleState.NO_CYCLE
        if (id == CycleState.NO_CYCLE) return@map CycleState.NONE
        val lat = prefs[Keys.LAT]
        val lng = prefs[Keys.LNG]
        CycleState(
            cycleId = id,
            startedAtEpochMs = prefs[Keys.STARTED_AT] ?: 0L,
            durationMinutes = prefs[Keys.DURATION] ?: 0,
            readyAtEpochMs = prefs[Keys.READY_AT] ?: 0L,
            deadlineEpochMs = prefs[Keys.DEADLINE] ?: 0L,
            location = if (lat != null && lng != null) {
                LaundryLocation(lat, lng, prefs[Keys.LABEL] ?: "Laundry room")
            } else {
                null
            },
            geofenceRadiusMeters = prefs[Keys.RADIUS] ?: 100f,
            ignoredChecks = prefs[Keys.IGNORED_CHECKS] ?: 0,
            arrivedAfterReady = prefs[Keys.ARRIVED_AFTER_READY] ?: false,
            reminderQueued = prefs[Keys.REMINDER_QUEUED] ?: false,
            washerLabel = prefs[Keys.WASHER_LABEL].orEmpty(),
        )
    }

    suspend fun current(): CycleState = state.first()

    suspend fun save(cycle: CycleState) {
        context.cycleDataStore.edit { prefs ->
            prefs[Keys.CYCLE_ID] = cycle.cycleId
            prefs[Keys.STARTED_AT] = cycle.startedAtEpochMs
            prefs[Keys.DURATION] = cycle.durationMinutes
            prefs[Keys.READY_AT] = cycle.readyAtEpochMs
            prefs[Keys.DEADLINE] = cycle.deadlineEpochMs
            cycle.location?.let {
                prefs[Keys.LAT] = it.latitude
                prefs[Keys.LNG] = it.longitude
                prefs[Keys.LABEL] = it.label
            }
            prefs[Keys.RADIUS] = cycle.geofenceRadiusMeters
            prefs[Keys.IGNORED_CHECKS] = cycle.ignoredChecks
            prefs[Keys.ARRIVED_AFTER_READY] = cycle.arrivedAfterReady
            prefs[Keys.REMINDER_QUEUED] = cycle.reminderQueued
            prefs[Keys.WASHER_LABEL] = cycle.washerLabel
        }
    }

    /** Applies [transform] to the stored cycle, returning the updated value, or null if no cycle is active. */
    suspend fun update(transform: (CycleState) -> CycleState): CycleState? {
        val existing = current()
        if (!existing.isActive) return null
        val updated = transform(existing)
        save(updated)
        return updated
    }

    suspend fun clear() {
        context.cycleDataStore.edit { it.clear() }
    }
}
