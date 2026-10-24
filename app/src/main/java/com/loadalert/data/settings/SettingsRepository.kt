package com.loadalert.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.loadalert.data.model.EscalationSettings
import com.loadalert.data.model.LaundryLocation
import com.loadalert.data.model.LaundrySettings
import com.loadalert.data.model.MessagingApp
import com.loadalert.data.model.QuietHours
import com.loadalert.data.model.RoommateContact
import com.loadalert.data.model.sanitizeWasherLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "loadalert_settings")

/** Reads and writes user preferences. The only source of truth for configuration. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_DURATION = intPreferencesKey("default_duration_minutes")
        val LOCATION_LAT = doublePreferencesKey("location_lat")
        val LOCATION_LNG = doublePreferencesKey("location_lng")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val GEOFENCE_RADIUS = floatPreferencesKey("geofence_radius_meters")
        val ROOMMATE_NAME = stringPreferencesKey("roommate_name")
        val ROOMMATE_VALUE = stringPreferencesKey("roommate_contact_value")
        val ROOMMATE_APP = stringPreferencesKey("roommate_app")
        val REMINDER_INTERVAL = intPreferencesKey("reminder_interval_minutes")
        val ESCALATE_AFTER = intPreferencesKey("escalate_after_ignored")
        val ESCALATE_SHORTEN = booleanPreferencesKey("escalate_shorten_interval")
        val ESCALATED_INTERVAL = intPreferencesKey("escalated_interval_minutes")
        val ESCALATE_URGENCY = booleanPreferencesKey("escalate_increase_urgency")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = intPreferencesKey("quiet_hours_start")
        val QUIET_END = intPreferencesKey("quiet_hours_end")
        val MAX_CYCLE = intPreferencesKey("max_cycle_minutes")
        val TRACK_WASHER = booleanPreferencesKey("track_washer_number")
        val LAST_WASHER = stringPreferencesKey("last_washer_label")
    }

    val settings: Flow<LaundrySettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): LaundrySettings = settings.first()

    private fun Preferences.toSettings(): LaundrySettings {
        val defaults = LaundrySettings()
        val lat = this[Keys.LOCATION_LAT]
        val lng = this[Keys.LOCATION_LNG]
        val location = if (lat != null && lng != null) {
            LaundryLocation(lat, lng, this[Keys.LOCATION_LABEL] ?: "Laundry room")
        } else {
            null
        }
        val roommateName = this[Keys.ROOMMATE_NAME]
        val roommate = if (!roommateName.isNullOrBlank()) {
            RoommateContact(
                name = roommateName,
                contactValue = this[Keys.ROOMMATE_VALUE].orEmpty(),
                app = this[Keys.ROOMMATE_APP]
                    ?.let { stored -> MessagingApp.entries.firstOrNull { it.name == stored } }
                    ?: MessagingApp.SMS,
            )
        } else {
            null
        }
        return LaundrySettings(
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            defaultDurationMinutes = this[Keys.DEFAULT_DURATION] ?: defaults.defaultDurationMinutes,
            laundryLocation = location,
            geofenceRadiusMeters = this[Keys.GEOFENCE_RADIUS] ?: defaults.geofenceRadiusMeters,
            roommate = roommate,
            reminderIntervalMinutes = this[Keys.REMINDER_INTERVAL] ?: defaults.reminderIntervalMinutes,
            escalation = EscalationSettings(
                escalateAfterIgnoredChecks = this[Keys.ESCALATE_AFTER]
                    ?: defaults.escalation.escalateAfterIgnoredChecks,
                shortenInterval = this[Keys.ESCALATE_SHORTEN] ?: defaults.escalation.shortenInterval,
                escalatedIntervalMinutes = this[Keys.ESCALATED_INTERVAL]
                    ?: defaults.escalation.escalatedIntervalMinutes,
                increaseUrgency = this[Keys.ESCALATE_URGENCY] ?: defaults.escalation.increaseUrgency,
            ),
            quietHours = QuietHours(
                enabled = this[Keys.QUIET_ENABLED] ?: defaults.quietHours.enabled,
                startMinuteOfDay = this[Keys.QUIET_START] ?: defaults.quietHours.startMinuteOfDay,
                endMinuteOfDay = this[Keys.QUIET_END] ?: defaults.quietHours.endMinuteOfDay,
            ),
            maxCycleMinutes = this[Keys.MAX_CYCLE] ?: defaults.maxCycleMinutes,
            trackWasherNumber = this[Keys.TRACK_WASHER] ?: defaults.trackWasherNumber,
            lastWasherLabel = this[Keys.LAST_WASHER] ?: defaults.lastWasherLabel,
        )
    }

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    suspend fun setDefaultDuration(minutes: Int) = edit { it[Keys.DEFAULT_DURATION] = minutes }

    suspend fun setLaundryLocation(location: LaundryLocation) = edit {
        it[Keys.LOCATION_LAT] = location.latitude
        it[Keys.LOCATION_LNG] = location.longitude
        it[Keys.LOCATION_LABEL] = location.label
    }

    suspend fun setGeofenceRadius(meters: Float) = edit { it[Keys.GEOFENCE_RADIUS] = meters }

    suspend fun setRoommate(contact: RoommateContact?) = edit { prefs ->
        if (contact == null) {
            prefs.remove(Keys.ROOMMATE_NAME)
            prefs.remove(Keys.ROOMMATE_VALUE)
            prefs.remove(Keys.ROOMMATE_APP)
        } else {
            prefs[Keys.ROOMMATE_NAME] = contact.name
            prefs[Keys.ROOMMATE_VALUE] = contact.contactValue
            prefs[Keys.ROOMMATE_APP] = contact.app.name
        }
    }

    suspend fun setReminderInterval(minutes: Int) = edit { it[Keys.REMINDER_INTERVAL] = minutes }

    suspend fun setEscalation(escalation: EscalationSettings) = edit {
        it[Keys.ESCALATE_AFTER] = escalation.escalateAfterIgnoredChecks
        it[Keys.ESCALATE_SHORTEN] = escalation.shortenInterval
        it[Keys.ESCALATED_INTERVAL] = escalation.escalatedIntervalMinutes
        it[Keys.ESCALATE_URGENCY] = escalation.increaseUrgency
    }

    suspend fun setQuietHours(quietHours: QuietHours) = edit {
        it[Keys.QUIET_ENABLED] = quietHours.enabled
        it[Keys.QUIET_START] = quietHours.startMinuteOfDay
        it[Keys.QUIET_END] = quietHours.endMinuteOfDay
    }

    suspend fun setMaxCycleMinutes(minutes: Int) = edit { it[Keys.MAX_CYCLE] = minutes }

    suspend fun setTrackWasherNumber(enabled: Boolean) = edit { it[Keys.TRACK_WASHER] = enabled }

    /** Remembers the machine used on the last load so the next one is pre-filled. */
    suspend fun setLastWasherLabel(label: String) = edit {
        it[Keys.LAST_WASHER] = sanitizeWasherLabel(label)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
