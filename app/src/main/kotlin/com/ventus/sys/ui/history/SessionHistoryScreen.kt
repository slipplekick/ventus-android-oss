package com.ventus.sys.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.service.SessionHistoryEntry
import com.ventus.sys.ui.common.verdictColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ROW_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)

/** Ports app.js's Session History page (renderHistory, app.js:625-665) — this session's scored tracks, newest first, with CSV export. */
@Composable
fun SessionHistoryScreen(viewModel: SessionHistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val context = LocalContext.current

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(viewModel.buildCsv().toByteArray())
                }
            }
        }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "SIGNAL // HISTORY", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (entries.isEmpty()) "No signals logged this session." else "${entries.size} signals logged this session.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { exportLauncher.launch("ventus_session_${System.currentTimeMillis()}.csv") },
                enabled = entries.isNotEmpty(),
            ) { Text("EXPORT CSV") }
            TextButton(
                onClick = viewModel::clear,
                enabled = entries.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("CLEAR") }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Play something to start logging signals.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(entries, key = { "${it.id}-${it.timestampMs}" }) { entry -> HistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: SessionHistoryEntry) {
    // Explicit Color.Transparent - Surface with no color param defaults to
    // MaterialTheme.colorScheme.surface, a visibly different shade from the
    // actual screen background - same fix as Signals/Master Vault's row
    // Surfaces.
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ROW_TIME_FORMAT.format(Date(entry.timestampMs)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = entry.artist, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = entry.score?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                color = entry.verdict?.let { verdictColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
