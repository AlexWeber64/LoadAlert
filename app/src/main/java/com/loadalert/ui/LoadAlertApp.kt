package com.loadalert.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.loadalert.di.container
import com.loadalert.ui.history.HistoryScreen
import com.loadalert.ui.home.HomeScreen
import com.loadalert.ui.onboarding.OnboardingScreen
import com.loadalert.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.LocalLaundryService),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

class RootViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.container.settingsRepository

    /** Null while we are still reading from disk, so we do not flash the wrong screen. */
    val onboardingComplete: StateFlow<Boolean?> = repository.settings
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadAlertApp(
    startCycleNow: Boolean = false,
    onStartCycleHandled: () -> Unit = {},
    viewModel: RootViewModel = viewModel(),
) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    when (onboardingComplete) {
        null -> Unit // First composition, still loading preferences.

        // OnboardingViewModel persists the flag itself; this flow then flips the screen.
        false -> OnboardingScreen(onFinished = {})

        true -> Scaffold(
            topBar = { TopAppBar(title = { Text(if (tab == Tab.HOME) "LoadAlert" else tab.label) }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val contentModifier = Modifier.fillMaxSize().padding(padding)
            when (tab) {
                Tab.HOME -> HomeScreen(
                    modifier = contentModifier,
                    onShowMessage = showMessage,
                    autoStart = startCycleNow,
                    onAutoStartHandled = onStartCycleHandled,
                )

                Tab.HISTORY -> HistoryScreen(modifier = contentModifier)

                Tab.SETTINGS -> SettingsScreen(
                    modifier = contentModifier,
                    onShowMessage = showMessage,
                )
            }
        }
    }
}
