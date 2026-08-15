package com.ventus.sys.ui.mastervault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.data.local.MasterVaultPlaylistSummary
import com.ventus.sys.data.repository.MasterVaultTrackUiItem
import kotlinx.coroutines.launch

private const val ALL_PLAYLISTS_LABEL = "◈ ALL PLAYLISTS — everything ever imported"

/** Ports app.js's Master Vault overlay (openMasterVault + friends, app.js:2594-2764). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterVaultScreen(viewModel: MasterVaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.playlistInput.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // buildCsv() is suspend (reads the DB fresh at export time, not off a
    // Flow already collected into state) - the SAF launcher callback itself
    // can't suspend, so the CSV has to be built in a coroutine and handed to
    // the OutputStream once both the content and the destination URI are
    // ready, not assumed to already be sitting in memory.
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let {
                coroutineScope.launch {
                    val csv = viewModel.buildCsv()
                    context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(csv.toByteArray()) }
                }
            }
        }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "⬡ MASTER VAULT", style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
                "Import any playlist to index it into your library, then browse that " +
                    "playlist on its own — separate from your main Taste Profile vault.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        val isSyncing = state.syncState is MasterVaultSyncState.Syncing
        ImportRow(input, isSyncing, viewModel)
        // Stacked vertically, not side-by-side in a Row - "IMPORT ALL MY
        // PLAYLISTS" already takes up most of the screen width on its own,
        // and cramming a second button next to it squeezes that one down to
        // near-zero width, wrapping its label one letter per line (the
        // exact bug already fixed twice elsewhere in this app - see
        // SignalsScreen's RangeSelector and this pattern's history).
        OutlinedButton(onClick = viewModel::importAll, enabled = !isSyncing, modifier = Modifier.padding(top = 8.dp)) {
            Text("⬡ IMPORT ALL MY PLAYLISTS")
        }
        OutlinedButton(
            onClick = { exportLauncher.launch("ventus_master_vault_${System.currentTimeMillis()}.csv") },
            enabled = state.stats.total > 0,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("⬇ EXPORT CSV")
        }

        when (val sync = state.syncState) {
            is MasterVaultSyncState.Error -> {
                Text(text = "// ${sync.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            is MasterVaultSyncState.Done -> {
                Text(text = "// ${sync.message}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            is MasterVaultSyncState.Syncing -> {
                sync.progress?.let {
                    Text(text = "// $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }

            is MasterVaultSyncState.Idle -> {
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
            // weight(1f) on the label, not the button - Row measures unweighted
            // children (the button) at their natural size first, then gives the
            // weighted Text whatever's left. Without this, neither child had a
            // weight, so on the "ALL PLAYLISTS" label + "REMOVE THIS PLAYLIST"
            // button combination there wasn't room for the button's normal pill
            // shape - it got squeezed down to a near-circular blob overlapping
            // the label.
            Text(
                text = if (state.selectedPlaylistId == null) "// INDEXED TRACKS — ALL PLAYLISTS" else "// TRACKS",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
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
    // Explicit Color.Transparent - Surface with no color param defaults to
    // MaterialTheme.colorScheme.surface, a visibly different (lighter,
    // blue-tinted) shade from the actual screen background, painting a
    // mismatched-color box behind every row. Same root cause and fix as
    // SignalsScreen's RecentRow.
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent) {
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
