package com.ventus.sys.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.domain.model.Verdict
import com.ventus.sys.ui.common.verdictColor

private const val KPI_GRID_COLUMNS = 3

/** Ports app.js's Playlist Audit page (runPlaylistAudit, app.js:1761-1841) — score an arbitrary playlist against the current profile. */
@Composable
fun PlaylistAuditScreen(viewModel: PlaylistAuditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val input by viewModel.playlistInput.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "SIGNAL // AUDIT", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Score any playlist against your current taste profile.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { viewModel.playlistInput.value = it },
                placeholder = { Text("Paste playlist ID or URL") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::runAudit,
                enabled = state !is AuditUiState.Auditing,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("AUDIT")
            }
        }

        when (val s = state) {
            AuditUiState.Idle -> Unit
            AuditUiState.Auditing -> AuditingState()
            is AuditUiState.Error -> ErrorState(s.message)
            is AuditUiState.Complete -> CompleteState(s)
        }
    }
}

@Composable
private fun AuditingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Scanning every track — this may take a moment…",
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Text(
        text = "// $message",
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun CompleteState(state: AuditUiState.Complete) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuditKpiGrid(state.summary)
        Text(
            text = "${state.tracks.size} tracks — sorted by score",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.tracks, key = { it.id }) { track -> AuditTrackRow(track) }
        }
    }
}

private data class AuditKpi(
    val label: String,
    val value: String,
)

@Composable
private fun AuditKpiGrid(summary: AuditSummary) {
    val kpis =
        listOf(
            AuditKpi("TRACKS", summary.totalTracks.toString()),
            AuditKpi("AVG SCORE", "${summary.avgScore}%"),
            AuditKpi("ALIGNED+", summary.alignedPlusCount.toString()),
            AuditKpi("CORE", summary.coreCount.toString()),
            AuditKpi("GHOSTS", summary.ghostCount.toString()),
            AuditKpi("COVERAGE", "${summary.coveragePercent}%"),
        )
    LazyVerticalGrid(
        columns = GridCells.Fixed(KPI_GRID_COLUMNS),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        items(kpis) { kpi ->
            Surface(modifier = Modifier.padding(2.dp), shape = RoundedCornerShape(4.dp), tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = kpi.label, style = MaterialTheme.typography.labelSmall)
                    Text(text = kpi.value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AuditTrackRow(track: AuditTrack) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        "${track.artist} · ${track.camelot}" +
                            if (!track.isGhost) " · NRG ${track.energy} · VAL ${track.valence}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = track.score?.let { "$it%" } ?: "GHOST",
                style = MaterialTheme.typography.titleMedium,
                color = track.verdict?.let { verdictColor(it) } ?: verdictColor(Verdict.NO_MATCH),
            )
        }
    }
}
