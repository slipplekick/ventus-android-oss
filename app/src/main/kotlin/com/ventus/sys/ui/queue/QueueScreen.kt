package com.ventus.sys.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.ui.common.verdictColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SYNC_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)

/** Ports app.js's Queue Analyzer page (loadQueue, app.js:1852-1904) — scores the next 15 tracks in your Spotify queue. */
@Composable
fun QueueScreen(viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "SIGNAL // QUEUE", style = MaterialTheme.typography.headlineSmall)
                StatusLine(state)
            }
            Button(onClick = viewModel::load, enabled = state !is QueueUiState.Loading) { Text("REFRESH") }
        }

        when (val s = state) {
            QueueUiState.Loading -> LoadingState()
            is QueueUiState.Error -> ErrorState(s.message)
            is QueueUiState.Loaded -> QueueList(s.tracks)
        }
    }
}

@Composable
private fun StatusLine(state: QueueUiState) {
    if (state is QueueUiState.Loaded) {
        Text(
            text = "${state.tracks.size} tracks scored · last sync ${SYNC_TIME_FORMAT.format(Date(state.lastSyncedAtMs))}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(text = "Fetching queue from Spotify…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QueueList(tracks: List<QueueTrackUiItem>) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Queue is empty.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
        // Keyed by (id, index) not just id — Spotify's queue can legitimately
        // contain the same track twice (queued manually more than once, or
        // repeat-one carrying a track into its own upcoming queue). A plain
        // id key crashes LazyColumn with "Key was already used" the moment
        // that happens.
        itemsIndexed(tracks, key = { index, track -> "${track.id}:$index" }) { _, track -> QueueRow(track) }
    }
}

@Composable
private fun QueueRow(track: QueueTrackUiItem) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        "${track.artist} · ${track.camelot}" +
                            (track.energy?.let { " · NRG $it" } ?: "") +
                            (track.valence?.let { " · VAL $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = track.score?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                color = track.verdict?.let { verdictColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
