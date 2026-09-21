package com.loadalert.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadalert.data.model.EscalationSettings
import com.loadalert.data.model.LaundrySettings
import com.loadalert.data.model.MessagingApp
import com.loadalert.data.model.QuietHours
import com.loadalert.ui.components.InfoText
import com.loadalert.messaging.RoommateMessenger
import com.loadalert.ui.components.DurationChips
import com.loadalert.ui.components.OptionChips
import com.loadalert.ui.components.SectionCard
import com.loadalert.ui.components.SwitchRow
import com.loadalert.ui.components.ThinDivider
import com.loadalert.ui.home.formatCoordinates

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onShowMessage: (String) -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect(onShowMessage)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ReliabilitySection()
        DefaultsSection(settings, viewModel)
        LaundryRoomSection(settings, viewModel)
        RoommateSection(settings, viewModel)
        RemindersSection(settings, viewModel)
        EscalationSection(settings, viewModel)
        QuietHoursSection(settings, viewModel)
        DangerSection(viewModel)
    }
}

/**
 * Surfaces battery optimisation, which silently decides whether reminders arrive on time.
 * Only shown while the app is still subject to it.
 */
@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    // Re-checked on each composition so the card disappears once the user returns from settings.
    var exempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exempt = BatteryOptimization.isExempt(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (exempt) return

    SectionCard(
        title = "Reminder reliability",
        subtitle = "Android is allowed to hold LoadAlert's timer back to save battery.",
    ) {
        Column {
            InfoText(
                "Your phone currently battery-optimises LoadAlert, which can delay the " +
                    "\"laundry is done\" reminder past the moment it is useful. Samsung devices " +
                    "are especially aggressive about this. Excluding LoadAlert costs almost " +
                    "nothing: it only runs a short check every few minutes while a load is on.",
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { BatteryOptimization.openSettings(context) }) {
                Text("Open battery settings")
            }
        }
    }
}

@Composable
private fun DefaultsSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    SectionCard(
        title = "Default wash length",
        subtitle = "Pre-selected on the home screen. You can still change it per load.",
    ) {
        Column {
            DurationChips(
                options = LaundrySettings.DURATION_CHOICES,
                selected = settings.defaultDurationMinutes,
                onSelect = viewModel::setDefaultDuration,
                dialogTitle = "Custom default length",
            )
            ThinDivider()
            SwitchRow(
                title = "Track washer number",
                description = "Ask which machine the load is in, and name it when you " +
                    "message your roommate.",
                checked = settings.trackWasherNumber,
                onCheckedChange = viewModel::setTrackWasherNumber,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaundryRoomSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    var showManual by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf(TextFieldValue("")) }
    var lngText by remember { mutableStateOf(TextFieldValue("")) }
    var labelText by remember { mutableStateOf(TextFieldValue("")) }

    SectionCard(
        title = "Laundry room",
        subtitle = "The point LoadAlert measures against to decide whether you are there.",
    ) {
        Column {
            val location = settings.laundryLocation
            Text(
                if (location == null) {
                    "Not set."
                } else {
                    location.label + "  (" + formatCoordinates(location.latitude, location.longitude) + ")"
                },
            )
            Spacer(Modifier.height(12.dp))
            // FlowRow, not Row: on a narrow screen a fixed row squeezes the second button
            // until its label breaks mid-word. Wrapping to the next line stays readable.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = viewModel::captureLaundryLocationHere) {
                    Text("I'm standing there now")
                }
                OutlinedButton(onClick = { showManual = !showManual }) {
                    Text(if (showManual) "Hide" else "Coordinates")
                }
            }

            if (showManual) {
                Spacer(Modifier.height(12.dp))
                InfoText(
                    "Paste coordinates from a maps app: long-press the laundry room, then copy " +
                        "the latitude and longitude.",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val lat = latText.text.trim().toDoubleOrNull()
                        val lng = lngText.text.trim().toDoubleOrNull()
                        if (lat != null && lng != null) {
                            viewModel.setLaundryLocationManually(lat, lng, labelText.text)
                            showManual = false
                        }
                    },
                ) {
                    Text("Save pin")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Geofence radius", style = MaterialTheme.typography.bodyLarge)
            InfoText("How close counts as \"at the laundry room\".")
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = LaundrySettings.RADIUS_CHOICES,
                selected = settings.geofenceRadiusMeters,
                onSelect = viewModel::setGeofenceRadius,
                label = { "${it.toInt()} m" },
            )
        }
    }
}

@Composable
private fun RoommateSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    val existing = settings.roommate
    var name by remember(existing) { mutableStateOf(TextFieldValue(existing?.name.orEmpty())) }
    var contact by remember(existing) {
        mutableStateOf(TextFieldValue(existing?.contactValue.orEmpty()))
    }
    var app by remember(existing) { mutableStateOf(existing?.app ?: MessagingApp.SMS) }

    SectionCard(
        title = "Roommate heads-up (optional)",
        subtitle = "When you start a load, LoadAlert opens your messaging app with a note ready " +
            "to send. Nothing is ever sent without you tapping send.",
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = MessagingApp.entries.toList(),
                selected = app,
                onSelect = { app = it },
                label = { it.displayName },
            )
            Spacer(Modifier.height(8.dp))
            InfoText(app.caveat)

            if (app.needsContactValue) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(if (app == MessagingApp.WHATSAPP) "Phone (with country code)" else "Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            val previewWasher = if (settings.trackWasherNumber) {
                settings.lastWasherLabel.ifBlank { "3" }
            } else {
                ""
            }
            Text(
                "On start: \"" +
                    RoommateMessenger.startMessage(settings.defaultDurationMinutes, previewWasher) +
                    "\"",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Mid-wash: \"" + RoommateMessenger.updateMessage(12, previewWasher) + "\"",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveRoommate(name.text, contact.text, app) }) {
                    Text("Save roommate")
                }
                if (existing != null) {
                    OutlinedButton(onClick = viewModel::removeRoommate) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun RemindersSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    SectionCard(
        title = "Reminder checks",
        subtitle = "How often LoadAlert re-checks whether you have collected the load.",
    ) {
        Column {
            OptionChips(
                options = LaundrySettings.INTERVAL_CHOICES,
                selected = settings.reminderIntervalMinutes,
                onSelect = viewModel::setReminderInterval,
                label = { "$it min" },
            )
            Spacer(Modifier.height(16.dp))
            Text("Give up after", style = MaterialTheme.typography.bodyLarge)
            InfoText("Measured from the moment you start the load.")
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = LaundrySettings.MAX_CYCLE_CHOICES,
                selected = settings.maxCycleMinutes,
                onSelect = viewModel::setMaxCycleMinutes,
                label = ::formatCycleLength,
            )
        }
    }
}

@Composable
private fun EscalationSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    val escalation = settings.escalation
    SectionCard(
        title = "Escalation",
        subtitle = "What happens once you have ignored a few reminders.",
    ) {
        Column {
            Text("Escalate after", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = listOf(1, 2, 3, 4, 5, 6),
                selected = escalation.escalateAfterIgnoredChecks,
                onSelect = { viewModel.setEscalation(escalation.copy(escalateAfterIgnoredChecks = it)) },
                label = { "$it" },
            )
            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "Louder notifications",
                description = "Switches to an alarm-style sound and a longer vibration.",
                checked = escalation.increaseUrgency,
                onCheckedChange = { viewModel.setEscalation(escalation.copy(increaseUrgency = it)) },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Check more often",
                description = "Shortens the gap between checks once escalated.",
                checked = escalation.shortenInterval,
                onCheckedChange = { viewModel.setEscalation(escalation.copy(shortenInterval = it)) },
            )
            if (escalation.shortenInterval) {
                Spacer(Modifier.height(12.dp))
                Text("Escalated interval", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                OptionChips(
                    options = listOf(1, 2, 3, 5),
                    selected = escalation.escalatedIntervalMinutes,
                    onSelect = { viewModel.setEscalation(escalation.copy(escalatedIntervalMinutes = it)) },
                    label = { "$it min" },
                )
            }
        }
    }
}

@Composable
private fun QuietHoursSection(settings: LaundrySettings, viewModel: SettingsViewModel) {
    val quiet = settings.quietHours
    SectionCard(
        title = "Quiet hours",
        subtitle = "Reminders inside this window are held, not dropped. They arrive when it ends.",
    ) {
        Column {
            SwitchRow(
                title = "Enable quiet hours",
                checked = quiet.enabled,
                onCheckedChange = { viewModel.setQuietHours(quiet.copy(enabled = it)) },
            )
            if (quiet.enabled) {
                Spacer(Modifier.height(16.dp))
                Text("From", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                OptionChips(
                    options = HOUR_CHOICES,
                    selected = quiet.startMinuteOfDay,
                    onSelect = { viewModel.setQuietHours(quiet.copy(startMinuteOfDay = it)) },
                    label = ::formatHour,
                )
                Spacer(Modifier.height(16.dp))
                Text("Until", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                OptionChips(
                    options = HOUR_CHOICES,
                    selected = quiet.endMinuteOfDay,
                    onSelect = { viewModel.setQuietHours(quiet.copy(endMinuteOfDay = it)) },
                    label = ::formatHour,
                )
            }
        }
    }
}

@Composable
private fun DangerSection(viewModel: SettingsViewModel) {
    SectionCard(title = "History") {
        TextButton(onClick = viewModel::clearHistory) { Text("Clear cycle history") }
    }
}

/** 90 minutes must not render as "1 h" next to a real 60-minute option. */
private fun formatCycleLength(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private val HOUR_CHOICES: List<Int> = (0..23).map { it * 60 }

private fun formatHour(minuteOfDay: Int): String =
    String.format(java.util.Locale.getDefault(), "%02d:00", minuteOfDay / 60)
