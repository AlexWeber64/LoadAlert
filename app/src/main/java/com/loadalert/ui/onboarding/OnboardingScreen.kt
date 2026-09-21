package com.loadalert.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadalert.data.model.LaundrySettings
import com.loadalert.data.model.MessagingApp
import com.loadalert.ui.components.InfoText
import com.loadalert.ui.components.DurationChips
import com.loadalert.ui.components.OptionChips
import com.loadalert.ui.components.SwitchRow
import com.loadalert.ui.home.formatCoordinates
import com.loadalert.ui.permissions.PermissionRequestCard
import com.loadalert.ui.permissions.missingPermissions
import com.loadalert.ui.settings.needsContactValue

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.ordinal + 1f) / OnboardingStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.DURATION -> DurationStep(state, viewModel)
                OnboardingStep.LOCATION -> LocationStep(state, viewModel)
                OnboardingStep.PERMISSIONS -> PermissionsStep()
                OnboardingStep.ROOMMATE -> RoommateStep(state, viewModel)
                OnboardingStep.DONE -> DoneStep(state)
            }

            Spacer(Modifier.height(32.dp))
            NavigationRow(state, viewModel, onFinished)
        }
    }
}

@Composable
private fun NavigationRow(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.step != OnboardingStep.WELCOME) {
            OutlinedButton(onClick = viewModel::back) { Text("Back") }
        }
        if (state.step == OnboardingStep.DONE) {
            Button(onClick = { viewModel.finish(onFinished) }, modifier = Modifier.weight(1f)) {
                Text("Start using LoadAlert")
            }
        } else {
            Button(
                onClick = viewModel::next,
                enabled = state.step != OnboardingStep.LOCATION || state.location != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Next")
            }
        }
    }
    if (state.step == OnboardingStep.LOCATION && state.location == null) {
        Spacer(Modifier.height(8.dp))
        InfoText("Pin the laundry room to continue. Everything else can be changed later.")
    }
}

@Composable
private fun WelcomeStep() {
    Column {
        Text("LoadAlert", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Shared laundry rooms punish anyone who forgets a load. LoadAlert starts a timer " +
                "when you put a wash on, then keeps reminding you until you have actually " +
                "collected it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Setup takes about a minute: how long your wash runs, where the laundry room is, " +
                "and optionally a roommate to tell.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DurationStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column {
        Text("How long is a typical wash?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        InfoText("This is only the default. You can change it for any individual load.")
        Spacer(Modifier.height(20.dp))
        DurationChips(
            options = LaundrySettings.DURATION_CHOICES,
            selected = state.durationMinutes,
            onSelect = viewModel::setDuration,
        )
    }
}

@Composable
private fun LocationStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    var showManual by remember { mutableStateOf(false) }
    var lat by remember { mutableStateOf(TextFieldValue("")) }
    var lng by remember { mutableStateOf(TextFieldValue("")) }
    var label by remember { mutableStateOf(TextFieldValue("")) }

    Column {
        Text("Where is the laundry room?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        InfoText(
            "LoadAlert compares your position against this point to work out whether you have " +
                "gone to collect your load. The position stays on your phone.",
        )
        Spacer(Modifier.height(20.dp))

        val location = state.location
        if (location != null) {
            Text("Pinned: " + location.label, style = MaterialTheme.typography.bodyLarge)
            InfoText(formatCoordinates(location.latitude, location.longitude))
            Spacer(Modifier.height(12.dp))
        }
        if (state.locationError != null) {
            Text(state.locationError, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Button(onClick = viewModel::captureHere, enabled = !state.capturing) {
            Text(if (state.capturing) "Getting a fix..." else "I'm standing there now")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showManual = !showManual }) {
            Text(if (showManual) "Hide manual entry" else "Enter coordinates instead")
        }

        if (showManual) {
            Spacer(Modifier.height(8.dp))
            InfoText("Long-press the laundry room in a maps app and copy the coordinates.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val latitude = lat.text.trim().toDoubleOrNull()
                    val longitude = lng.text.trim().toDoubleOrNull()
                    if (latitude != null && longitude != null) {
                        viewModel.setManualLocation(latitude, longitude, label.text)
                        showManual = false
                    }
                },
            ) {
                Text("Use these coordinates")
            }
        }
    }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var epoch by remember { mutableIntStateOf(0) }
    val missing = remember(epoch) { missingPermissions(context) }

    Column {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        InfoText(
            "LoadAlert needs to send notifications, and to read your location while it is " +
                "closed. Without background location it cannot tell whether you have picked " +
                "up your laundry, so it would just nag you blindly.",
        )
        Spacer(Modifier.height(20.dp))

        if (missing.isEmpty()) {
            Text("All set.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                missing.forEach { permission ->
                    PermissionRequestCard(permission = permission, onResult = { epoch++ })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoText("You can skip these for now and grant them later from the home screen.")
    }
}

@Composable
private fun RoommateStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column {
        Text("Tell a roommate? (optional)", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        InfoText(
            "If you turn this on, starting a load opens your messaging app with a short note " +
                "ready to go. LoadAlert never sends anything by itself, and it does not read " +
                "your messages or contacts.",
        )
        Spacer(Modifier.height(20.dp))

        SwitchRow(
            title = "Send a heads-up when I start a load",
            checked = state.roommateEnabled,
            onCheckedChange = viewModel::setRoommateEnabled,
        )

        if (state.roommateEnabled) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.roommateName,
                onValueChange = viewModel::setRoommateName,
                label = { Text("Their name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("Send with", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = MessagingApp.entries.toList(),
                selected = state.messagingApp,
                onSelect = viewModel::setMessagingApp,
                label = { it.displayName },
            )
            Spacer(Modifier.height(8.dp))
            InfoText(state.messagingApp.caveat)

            if (state.messagingApp.needsContactValue) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.roommateContact,
                    onValueChange = viewModel::setRoommateContact,
                    label = {
                        Text(
                            if (state.messagingApp == MessagingApp.WHATSAPP) {
                                "Phone with country code"
                            } else {
                                "Phone number"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("They will get:", style = MaterialTheme.typography.bodyMedium)
            Text(
                "\"Started a load, ready in ${state.durationMinutes} min\"",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DoneStep(state: OnboardingUiState) {
    Column {
        Text("Ready", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Default wash: ${state.durationMinutes} min")
        Text(
            "Laundry room: " + state.location?.let {
                formatCoordinates(it.latitude, it.longitude)
            }.orEmpty().ifEmpty { "not set" },
        )
        Text(
            "Roommate: " + if (state.roommateEnabled && state.roommateName.isNotBlank()) {
                "${state.roommateName} via ${state.messagingApp.displayName}"
            } else {
                "off"
            },
        )
        Spacer(Modifier.height(16.dp))
        InfoText("Everything here can be changed later in Settings.")
    }
}
