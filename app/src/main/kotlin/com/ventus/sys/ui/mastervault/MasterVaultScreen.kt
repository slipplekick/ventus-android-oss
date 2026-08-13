package com.ventus.sys.ui.mastervault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.data.local.MasterVaultPlaylistSummary
import com.ventus.sys.data.repository.MasterVaultTrackUiItem

private const val ALL_PLAYLISTS_LABEL = "◈ ALL PLAYLISTS — everything ever imported"

/** Ports app.js's Master Vault overlay (openMasterVault + friends, app.js:2594-2764). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterVaultScreen(viewModel: MasterVaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.playlistInput.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "⬡ MASTER VAULT", style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
                "Import any playlist to index it into your library, then browse that " +
                    "playlist on its own — separate from your main Taste Profile vault.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        ImportRow(input, state.syncState is MasterVaultSyncState.Syncing, viewModel)

        when (val sync = state.syncState) {
            is MasterVaultSyncState.Error -> {
                Text(text = "// ${sync.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            is MasterVaultSyncState.Done -> {
                Text(text = "// ${sync.message}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            else -> {
                Unit
            }
        }

        PlaylistFilter(state.playlists, state.selectedPlaylistId, viewModel::selectPlaylist, modifier = Modifier.padding(top = 12.dp))

        Text(
            text = "${state.stats.total} tracks total — ${state.stats.indexed} indexed, ${state.stats.ghost} ghost",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.selectedPlaylistId == null) "// INDEXED TRACKS — ALL PLAYLISTS" else "// TRACKS",
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(onClick = viewModel::clearSelected) {
                Text(if (state.selectedPlaylistId == null) "✕ CLEAR ALL" else "✕ REMOVE THIS PLAYLIST")
            }
        }

        TrackList(state.tracks)
    }
}

@Composable
private fun ImportRow(
    input: String,
    isSyncing: Boolean,
    viewModel: MasterVaultViewModel,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { viewModel.playlistInput.value = it },
            placeholder = { Text("Spotify playlist ID or URL") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = viewModel::sync, enabled = !isSyncing, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (isSyncing) "IMPORTING…" else "⬡ IMPORT")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistFilter(
    playlists: List<MasterVaultPlaylistSummary>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        playlists.find { it.playlistId == selectedId }?.let { "${it.playlistName} (${it.trackCount})" } ?: ALL_PLAYLISTS_LABEL

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("VIEW") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(ALL_PLAYLISTS_LABEL) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            playlists.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.playlistName} (${p.trackCount})") },
                    onClick = {
                        onSelect(p.playlistId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackList(tracks: List<MasterVaultTrackUiItem>) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No tracks yet. Import a playlist above.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn {
        items(tracks, key = { it.id }) { track -> TrackRow(track) }
    }
}

@Composable
private fun TrackRow(track: MasterVaultTrackUiItem) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.song.ifBlank { track.id }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        "${track.artist} · ${track.camelot}" +
                            (track.energy?.let { " · NRG $it" } ?: "") +
                            (track.valence?.let { " · VAL $it" } ?: "") +
                            (track.bpm?.let { " · $it BPM" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (track.isGhost) {
                Text(text = "GHOST", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
