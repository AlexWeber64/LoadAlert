package com.loadalert.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loadalert.data.model.CycleState
import com.loadalert.data.model.LaundrySettings
import com.loadalert.di.container
import com.loadalert.domain.CycleCoordinator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: LaundrySettings = LaundrySettings(),
    val cycle: CycleState = CycleState.NONE,
    val selectedDurationMinutes: Int = LaundrySettings().defaultDurationMinutes,
    val isStarting: Boolean = false,
)

/** One-shot things the screen has to do that a ViewModel cannot do itself. */
sealed interface HomeEvent {
    data class LaunchMessaging(val intent: Intent) : HomeEvent
    data class ShowMessage(val text: String) : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    private val coordinator: CycleCoordinator = container.cycleCoordinator

    private val durationOverride = MutableStateFlow<Int?>(null)
    private val starting = MutableStateFlow(false)

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Ticks once a second so the countdown on screen stays live. */
    private val ticker = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = ticker.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        container.settingsRepository.settings,
        container.cycleRepository.state,
        durationOverride,
        starting,
    ) { settings, cycle, override, isStarting ->
        HomeUiState(
            settings = settings,
            cycle = cycle,
            selectedDurationMinutes = override ?: settings.defaultDurationMinutes,
            isStarting = isStarting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                ticker.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun setDuration(minutes: Int) {
        durationOverride.value = minutes
    }

    fun startLaundry() {
        if (starting.value) return
        starting.value = true
        viewModelScope.launch {
            try {
                when (val result = coordinator.startCycle(durationOverride.value)) {
                    is CycleCoordinator.StartResult.Started -> {
                        durationOverride.value = null
                        result.messagingIntent?.let { events.send(HomeEvent.LaunchMessaging(it)) }
                    }

                    CycleCoordinator.StartResult.NoLocation -> events.send(
                        HomeEvent.ShowMessage(
                            "Could not get a location fix. Set the laundry room in Settings, " +
                                "or try again with location turned on.",
                        ),
                    )

                    is CycleCoordinator.StartResult.AlreadyRunning -> events.send(
                        HomeEvent.ShowMessage("A cycle is already running."),
                    )
                }
            } finally {
                starting.value = false
            }
        }
    }

    fun markCollected() {
        viewModelScope.launch { coordinator.markCollected() }
    }

    fun cancelCycle() {
        viewModelScope.launch { coordinator.cancelCycle() }
    }

    /** Re-pins the laundry room to wherever the phone is right now. */
    fun captureCurrentLocationAsLaundryRoom() {
        viewModelScope.launch {
            val captured = container.locationClient.captureLaundryLocation()
            if (captured == null) {
                events.send(HomeEvent.ShowMessage("No location fix yet. Try again in a moment."))
            } else {
                container.settingsRepository.setLaundryLocation(captured)
                events.send(HomeEvent.ShowMessage("Laundry room saved at your current position."))
            }
        }
    }
}
