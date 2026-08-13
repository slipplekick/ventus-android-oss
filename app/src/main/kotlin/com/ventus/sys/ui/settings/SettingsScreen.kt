package com.ventus.sys.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.data.local.AppPreferences
import com.ventus.sys.data.local.PlaylistPresetEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INTERVAL_PRESETS_SECONDS = listOf(60, 300, 600, 1800)
private val LAST_RUN_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)

private fun formatIntervalLabel(seconds: Int): String =
    if (seconds < SECONDS_PER_MINUTE) "${seconds}s" else "${seconds / SECONDS_PER_MINUTE} min"

private const val SECONDS_PER_MINUTE = 60

/**
 * Ports app.js's auto-sync toggle UI (toggleAutoSync, app.js:839-863) and
 * Auto-Add's sidebar controls (app.js:1510-1671) — both live in the same
 * persistent sidebar panel on desktop, so both sit on this one screen here.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text(text = "SYSTEM // SETTINGS", style = MaterialTheme.typography.headlineSmall) }
        item { AutoAddSection(state, viewModel) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp)) }
        item { AutoSyncSection(state, viewModel) }
    }
}

@Composable
private fun AutoSyncSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Column {
        Text(text = "Playlist Auto-Sync", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Periodically re-syncs your taste-profile playlist so new tracks get picked up automatically.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Enabled", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.autoSyncEnabled, onCheckedChange = viewModel::setEnabled)
        }

        OutlinedTextField(
            value = state.playlistInput,
            onValueChange = viewModel::onPlaylistInputChanged,
            label = { Text("Target playlist ID or URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (state.enableError != null) {
            Text(
                text = "// ${state.enableError}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(text = "Check every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            INTERVAL_PRESETS_SECONDS.forEach { seconds ->
                FilterChip(
                    selected = state.intervalSeconds == seconds,
                    onClick = { viewModel.onIntervalChanged(seconds) },
                    label = { Text(formatIntervalLabel(seconds)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        if (state.status.lastMessage != null) {
            Text(
                text =
                    "// ${state.status.lastMessage}" +
                        (state.status.lastRunAtMs?.let { " (${LAST_RUN_FORMAT.format(Date(it))})" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun AutoAddSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(text = "Auto-Add", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Adds any track that scores at or above the threshold straight to a target playlist as it plays.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Enabled", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.autoAddEnabled, onCheckedChange = viewModel::setAutoAddEnabled)
        }

        Text(
            text = "Threshold: ${state.autoAddThreshold}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Slider(
            value = state.autoAddThreshold.toFloat(),
            onValueChange = { viewModel.onAutoAddThresholdChanged(it.toInt()) },
            valueRange = AppPreferences.MIN_AUTOADD_THRESHOLD.toFloat()..AppPreferences.MAX_AUTOADD_THRESHOLD.toFloat(),
            steps = AppPreferences.MAX_AUTOADD_THRESHOLD - AppPreferences.MIN_AUTOADD_THRESHOLD - 1,
        )

        PresetPicker(state, viewModel)

        OutlinedTextField(
            value = state.autoAddPlaylistInput,
            onValueChange = viewModel::onAutoAddPlaylistInputChanged,
            label = { Text("Target playlist ID or URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        if (state.autoAddError != null) {
            Text(
                text = "// ${state.autoAddError}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.autoAddStatus.lastMessage != null) {
            Text(
                text =
                    "// ${state.autoAddStatus.lastMessage}" +
                        (state.autoAddStatus.lastRunAtMs?.let { " (${LAST_RUN_FORMAT.format(Date(it))})" } ?: ""),
                color = if (state.autoAddStatus.lastWasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        PresetManager(state, viewModel)
    }
}

/** Ports app.js's autoAddPlaylistPreset select (index.html:133-136, handlePlaylistPreset app.js:1549-1560). */
@Composable
private fun PresetPicker(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    if (state.presets.isEmpty()) return
    Text(text = "Saved playlists", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        state.presets.forEach { preset ->
            FilterChip(
                selected = state.autoAddPlaylistInput == preset.playlistId,
                onClick = { viewModel.selectPreset(preset) },
                label = { Text(preset.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/** Ports app.js's preset manager overlay (openPresetManager/renderPresetList/addPreset/deletePreset, app.js:1562-1619). */
@Composable
private fun PresetManager(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Text(text = "Manage saved playlists", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 20.dp))

    state.presets.forEach { preset -> PresetRow(preset, viewModel) }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.newPresetName,
            onValueChange = viewModel::onNewPresetNameChanged,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.newPresetId,
            onValueChange = viewModel::onNewPresetIdChanged,
            label = { Text("Playlist ID or URL") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedButton(onClick = viewModel::saveNewPreset, modifier = Modifier.padding(top = 8.dp)) {
        Text("SAVE PRESET")
    }
}

@Composable
private fun PresetRow(
    preset: PlaylistPresetEntity,
    viewModel: SettingsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = preset.name, style = MaterialTheme.typography.bodyMedium)
            Text(text = preset.playlistId, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { viewModel.deletePreset(preset) }) {
            Icon(Icons.Filled.Close, contentDescription = "Delete preset")
        }
    }
}
