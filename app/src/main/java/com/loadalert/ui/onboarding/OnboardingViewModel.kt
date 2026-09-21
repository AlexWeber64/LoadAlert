package com.loadalert.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loadalert.data.model.LaundryLocation
import com.loadalert.data.model.MessagingApp
import com.loadalert.data.model.RoommateContact
import com.loadalert.di.container
import com.loadalert.location.LocationPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Order matters: PERMISSIONS comes before LOCATION because the location step captures a GPS
 * fix, which is impossible until location permission has been granted.
 */
enum class OnboardingStep {
    WELCOME,
    DURATION,
    PERMISSIONS,
    LOCATION,
    ROOMMATE,
    DONE,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val durationMinutes: Int = 45,
    val location: LaundryLocation? = null,
    val capturing: Boolean = false,
    val locationError: String? = null,
    val roommateName: String = "",
    val roommateContact: String = "",
    val messagingApp: MessagingApp = MessagingApp.SMS,
    val roommateEnabled: Boolean = false,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = container.settingsRepository.current()
            _state.update {
                it.copy(
                    durationMinutes = existing.defaultDurationMinutes,
                    location = existing.laundryLocation,
                    roommateEnabled = existing.roommate != null,
                    roommateName = existing.roommate?.name.orEmpty(),
                    roommateContact = existing.roommate?.contactValue.orEmpty(),
                    messagingApp = existing.roommate?.app ?: MessagingApp.SMS,
                )
            }
        }
    }

    fun setDuration(minutes: Int) = _state.update { it.copy(durationMinutes = minutes) }

    fun setRoommateName(value: String) = _state.update { it.copy(roommateName = value) }

    fun setRoommateContact(value: String) = _state.update { it.copy(roommateContact = value) }

    fun setMessagingApp(app: MessagingApp) = _state.update { it.copy(messagingApp = app) }

    fun setRoommateEnabled(enabled: Boolean) = _state.update { it.copy(roommateEnabled = enabled) }

    fun setManualLocation(latitude: Double, longitude: Double, label: String) = _state.update {
        it.copy(
            location = LaundryLocation(latitude, longitude, label.ifBlank { "Laundry room" }),
            locationError = null,
        )
    }

    fun captureHere() {
        if (_state.value.capturing) return
        if (!LocationPermissions.hasForegroundLocation(getApplication())) {
            // Without this the failure below reads "check that location is on", which sends
            // the user hunting through system settings for the wrong thing.
            _state.update {
                it.copy(
                    locationError = "LoadAlert does not have location permission yet. " +
                        "Go back one step to grant it, or enter coordinates instead.",
                )
            }
            return
        }
        _state.update { it.copy(capturing = true, locationError = null) }
        viewModelScope.launch {
            val captured = container.locationClient.captureLaundryLocation()
            _state.update {
                it.copy(
                    capturing = false,
                    location = captured ?: it.location,
                    locationError = if (captured == null) {
                        "No fix yet. Check that location is on, then try again."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun next() = _state.update { it.copy(step = it.step.next()) }

    fun back() = _state.update { it.copy(step = it.step.previous()) }

    /** Writes everything collected during setup and flips the onboarding flag. */
    fun finish(onComplete: () -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            val repository = container.settingsRepository
            repository.setDefaultDuration(current.durationMinutes)
            current.location?.let { repository.setLaundryLocation(it) }
            if (current.roommateEnabled && current.roommateName.isNotBlank()) {
                repository.setRoommate(
                    RoommateContact(
                        name = current.roommateName.trim(),
                        contactValue = current.roommateContact.trim(),
                        app = current.messagingApp,
                    ),
                )
            } else {
                repository.setRoommate(null)
            }
            repository.setOnboardingComplete(true)
            onComplete()
        }
    }
}

private fun OnboardingStep.next(): OnboardingStep =
    OnboardingStep.entries.getOrElse(ordinal + 1) { OnboardingStep.DONE }

private fun OnboardingStep.previous(): OnboardingStep =
    OnboardingStep.entries.getOrElse(ordinal - 1) { OnboardingStep.WELCOME }
