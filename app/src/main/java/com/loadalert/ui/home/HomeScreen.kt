package com.loadalert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadalert.data.model.CycleState
import com.loadalert.data.model.LaundrySettings
import com.loadalert.ui.components.BigCountdown
import com.loadalert.ui.components.InfoText
import com.loadalert.ui.components.OptionChips
import com.loadalert.ui.components.SectionCard
import com.loadalert.ui.permissions.PermissionRequestCard
import com.loadalert.ui.permissions.missingPermissions
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onShowMessage: (String) -> Unit = {},
    autoStart: Boolean = false,
    onAutoStartHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    // Recomputed whenever a permission card reports back.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val missing = remember(permissionEpoch, now / 5_000) { missingPermissions(context) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.LaunchMessaging -> runCatching {
                    context.startActivity(event.intent)
                }.onFailure {
                    onShowMessage("Could not open your messaging app.")
                }

                is HomeEvent.ShowMessage -> onShowMessage(event.text)
            }
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart) {
            viewModel.startLaundry()
            onAutoStartHandled()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Ask for one permission at a time; stacking three red cards helps nobody.
        missing.firstOrNull()?.let { permission ->
            PermissionRequestCard(permission = permission, onResult = { permissionEpoch++ })
        }

        if (state.cycle.isActive) {
            ActiveCycleCard(
                cycle = state.cycle,
                now = now,
                onCollected = viewModel::markCollected,
                onCancel = viewModel::cancelCycle,
            )
        } else {
            StartCycleCard(
                state = state,
                onDurationChange = viewModel::setDuration,
                onStart = viewModel::startLaundry,
            )
        }

        LaundryRoomCard(
            settings = state.settings,
            onCaptureHere = viewModel::captureCurrentLocationAsLaundryRoom,
        )

        RoommateCard(settings = state.settings, duration = state.selectedDurationMinutes)
    }
}

@Composable
private fun StartCycleCard(
    state: HomeUiState,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    SectionCard(
        title = "Start a load",
        subtitle = "This cycle only. Your default stays " +
            "${state.settings.defaultDurationMinutes} min.",
    ) {
        Column {
            OptionChips(
                options = LaundrySettings.DURATION_CHOICES,
                selected = state.selectedDurationMinutes,
                onSelect = onDurationChange,
                label = { "$it min" },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                enabled = !state.isStarting,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isStarting) "Starting..." else "Start Laundry")
            }
        }
    }
}

@Composable
private fun ActiveCycleCard(
    cycle: CycleState,
    now: Long,
    onCollected: () -> Unit,
    onCancel: () -> Unit,
) {
    val remaining = (cycle.readyAtEpochMs - now).coerceAtLeast(0L)
    val ready = remaining == 0L

    SectionCard(
        title = if (ready) "Waiting for pick-up" else "Wash running",
        subtitle = if (ready) {
            if (cycle.arrivedAfterReady) {
                "You are at the laundry room. LoadAlert will close this out when you leave."
            } else {
                "Reminders are running until you tap \"I got it\"."
            }
        } else {
            "Started at " + formatClock(cycle.startedAtEpochMs)
        },
    ) {
        Column {
            BigCountdown(
                text = if (ready) "Ready" else formatRemaining(remaining),
                caption = if (ready) {
                    "Finished at " + formatClock(cycle.readyAtEpochMs)
                } else {
                    "Ready at " + formatClock(cycle.readyAtEpochMs)
                },
            )
            if (ready && cycle.ignoredChecks > 0) {
                Spacer(Modifier.height(8.dp))
                InfoText("Reminders sent: ${cycle.ignoredChecks}")
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCollected, modifier = Modifier.weight(1f)) {
                    Text("I got it")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel cycle")
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoText("Gives up at " + formatClock(cycle.deadlineEpochMs) + ".")
        }
    }
}

@Composable
private fun LaundryRoomCard(settings: LaundrySettings, onCaptureHere: () -> Unit) {
    SectionCard(title = "Laundry room") {
        Column {
            val location = settings.laundryLocation
            if (location == null) {
                Text("Not set yet. LoadAlert will pin it when you start your first load.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(location.label, style = MaterialTheme.typography.bodyLarge)
                        InfoText(formatCoordinates(location.latitude, location.longitude))
                    }
                }
                Spacer(Modifier.height(4.dp))
                InfoText("Geofence radius ${settings.geofenceRadiusMeters.toInt()} m")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCaptureHere) { Text("I'm standing there now") }
        }
    }
}

@Composable
private fun RoommateCard(settings: LaundrySettings, duration: Int) {
    val roommate = settings.roommate ?: return
    SectionCard(
        title = "Roommate heads-up",
        subtitle = "Sent to ${roommate.name} via ${roommate.app.displayName} when you start.",
    ) {
        Column {
            Text("\"Started a load, ready in $duration min\"")
            Spacer(Modifier.height(8.dp))
            InfoText(roommate.app.caveat)
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
}

internal fun formatClock(epochMs: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
}

internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(java.util.Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
