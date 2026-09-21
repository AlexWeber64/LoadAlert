package com.loadalert.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loadalert.data.model.EscalationSettings
import com.loadalert.data.model.LaundrySettings
import com.loadalert.data.model.MessagingApp
import com.loadalert.data.model.QuietHours
import com.loadalert.data.model.RoommateContact
import com.loadalert.di.container
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    private val repository = container.settingsRepository

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val settings: StateFlow<LaundrySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LaundrySettings())

    fun setDefaultDuration(minutes: Int) = launchEdit { repository.setDefaultDuration(minutes) }

    fun setReminderInterval(minutes: Int) = launchEdit { repository.setReminderInterval(minutes) }

    fun setMaxCycleMinutes(minutes: Int) = launchEdit { repository.setMaxCycleMinutes(minutes) }

    fun setGeofenceRadius(meters: Float) = launchEdit { repository.setGeofenceRadius(meters) }

    fun setEscalation(escalation: EscalationSettings) = launchEdit { repository.setEscalation(escalation) }

    fun setQuietHours(quietHours: QuietHours) = launchEdit { repository.setQuietHours(quietHours) }

    fun saveRoommate(name: String, contactValue: String, app: MessagingApp) = launchEdit {
        if (name.isBlank()) {
            messages.send("Give your roommate a name first.")
            return@launchEdit
        }
        if (app.needsContactValue && contactValue.isBlank()) {
            messages.send("${app.displayName} needs a phone number to open the right chat.")
            return@launchEdit
        }
        repository.setRoommate(RoommateContact(name.trim(), contactValue.trim(), app))
        if (!container.roommateMessenger.isAppInstalled(app)) {
            messages.send("${app.displayName} does not look installed. LoadAlert will fall back to the share sheet.")
        } else {
            messages.send("Roommate saved.")
        }
    }

    fun removeRoommate() = launchEdit {
        repository.setRoommate(null)
        messages.send("Roommate removed. No messages will be sent.")
    }

    fun captureLaundryLocationHere() = launchEdit {
        val captured = container.locationClient.captureLaundryLocation()
        if (captured == null) {
            messages.send("No location fix yet. Make sure location is on and try again.")
        } else {
            repository.setLaundryLocation(captured)
            messages.send("Laundry room updated.")
        }
    }

    fun setLaundryLocationManually(latitude: Double, longitude: Double, label: String) = launchEdit {
        repository.setLaundryLocation(
            com.loadalert.data.model.LaundryLocation(latitude, longitude, label.ifBlank { "Laundry room" }),
        )
        messages.send("Laundry room updated.")
    }

    fun clearHistory() = launchEdit {
        container.historyRepository.clear()
        messages.send("History cleared.")
    }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** SMS and WhatsApp address a specific number; the share-sheet apps do not. */
val MessagingApp.needsContactValue: Boolean
    get() = this == MessagingApp.SMS || this == MessagingApp.WHATSAPP
