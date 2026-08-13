package com.ventus.sys.ui.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ventus.sys.ui.common.verdictColor

private val ALBUM_ART_SIZE = 48.dp

/** Ports app.js's Discover page — search (runDiscoverSearch), scan-to-score (scoreDiscoverTrack), neighbor ranking (renderNeighbors). */
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel = hiltViewModel()) {
    val query by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val neighbors by viewModel.neighbors.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "SIGNAL // DISCOVER", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Search Spotify, scan a track's signal, and see the closest matches in your library.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search track, artist…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::search,
                enabled = !isSearching,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("SCAN") }
        }

        if (searchError != null) {
            Text(text = "// $searchError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (isSearching) {
                item { LoadingRow("Scanning Spotify…") }
            }
            items(results, key = { it.id }) { result ->
                SearchResultRow(result, onScan = { viewModel.scanTrack(result.id) })
            }
            if (neighbors.isNotEmpty()) {
                item {
                    Text(
                        text = "CLOSEST MATCHES IN YOUR INDEX",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(neighbors, key = { "neighbor-${it.id}" }) { neighbor -> NeighborRow(neighbor) }
            }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SearchResultRow(
    result: DiscoverSearchResult,
    onScan: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result.albumArtUrl != null) {
                AsyncImage(
                    model = result.albumArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(ALBUM_ART_SIZE).padding(end = 12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = result.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${result.artist} · ${result.album}", style = MaterialTheme.typography.bodySmall)
            }
            when {
                result.isScanning -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }

                result.scanned -> {
                    Text(
                        text = result.score?.let { "$it%" } ?: "GHOST",
                        style = MaterialTheme.typography.titleMedium,
                        color = result.verdict?.let { verdictColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    OutlinedButton(onClick = onScan) { Text("SCAN SIGNAL") }
                }
            }
        }
    }
}

@Composable
private fun NeighborRow(neighbor: NeighborUiItem) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = neighbor.song, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        "${neighbor.artist} · ${neighbor.camelot} · NRG ${neighbor.energy} · VAL ${neighbor.valence} " +
                            "· BPM ${neighbor.bpm} · Δ${neighbor.distance}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${neighbor.score}%",
                style = MaterialTheme.typography.titleMedium,
                color = verdictColor(neighbor.verdict),
            )
        }
    }
}
