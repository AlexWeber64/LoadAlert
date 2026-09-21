package com.loadalert.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loadalert.data.history.CycleRecord
import com.loadalert.data.model.CycleOutcome
import com.loadalert.ui.components.InfoText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()

    if (records.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No loads yet.\nFinished cycles show up here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(records) }
        items(records, key = { it.cycleId }) { record -> HistoryRow(record) }
    }
}

@Composable
private fun SummaryCard(records: List<CycleRecord>) {
    val onTime = records.count { it.pickedUpOnTime }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(plural(records.size, "load") + " tracked", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            InfoText("$onTime collected promptly, ${records.size - onTime} left sitting.")
        }
    }
}

@Composable
private fun HistoryRow(record: CycleRecord) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (record.pickedUpOnTime) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (record.pickedUpOnTime) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDateTime(record.startedAtEpochMs),
                    style = MaterialTheme.typography.bodyLarge,
                )
                InfoText(
                    "${record.durationMinutes} min wash at ${record.locationLabel}",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    outcomeLine(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.pickedUpOnTime) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

private fun outcomeLine(record: CycleRecord): String {
    val delay = formatDuration(record.pickupDelayMs)
    return when (record.outcome) {
        CycleOutcome.ALREADY_THERE -> "You were already there when it finished"
        CycleOutcome.ACKNOWLEDGED -> "Marked collected after $delay"
        CycleOutcome.CONFIRMED_COLLECTED -> "Pick-up confirmed after $delay"
        CycleOutcome.TIMED_OUT -> "Timed out after $delay with no confirmation"
        CycleOutcome.CANCELLED -> "Cancelled"
        CycleOutcome.IN_PROGRESS -> "Still running"
    } + if (record.remindersSent > 0) " (" + plural(record.remindersSent, "reminder") + ")" else ""
}

// Built per call rather than cached in a top-level val: a cached formatter would keep
// whatever locale was current when the class loaded, and go stale if the user changes it.
private fun formatDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()))

private fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    if (minutes < 1) return "under a minute"
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours h" else "$hours h $rest min"
}
