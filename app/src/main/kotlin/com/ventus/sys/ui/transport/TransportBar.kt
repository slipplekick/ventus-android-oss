package com.ventus.sys.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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

private const val MIN_DURATION_MS = 1L

/**
 * Persistent transport deck — ports app.js's #transportDeck (app.js:1372-
 * 1430's togglePlayPause/sendPlayback + the seek bar). Mounted once in
 * [com.ventus.sys.ui.navigation.VentusNavHost]'s Scaffold, above the bottom
 * nav bar, so it's visible across every screen the way Spotify's own
 * now-playing bar is - not scoped to just the Live Scorer screen.
 *
 * Renders nothing when nothing is playing (no track name to show, no useful
 * controls to offer) rather than an empty/disabled-looking bar taking up
 * space permanently.
 *
 * Explicitly pads for the navigation-bar inset — MainActivity calls
 * enableEdgeToEdge(), so without this the Prev/Play/Next row draws under
 * the system nav bar instead of above it. Material3's own NavigationBar
 * composable does this internally; a custom composable like this one has
 * to opt in itself. Without it (confirmed on a 3-button-nav device), taps
 * on Next/Play land on the system nav bar instead of this row — a real,
 * reproducible dead-tap-target bug, not a hypothetical.
 */
@Composable
fun TransportBar(viewModel: TransportBarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    if (state.trackName == null) return

    var dragPositionMs by remember { mutableStateOf<Float?>(null) }

    Surface(tonalElevation = 3.dp, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = "${state.trackName} — ${state.artist.orEmpty()}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )

            val durationMs = state.durationMs.coerceAtLeast(MIN_DURATION_MS)
            val sliderPosition = dragPositionMs ?: state.progressMs.toFloat()
            Slider(
                value = sliderPosition.coerceIn(0f, durationMs.toFloat()),
                valueRange = 0f..durationMs.toFloat(),
                onValueChange = {
                    viewModel.isSeekDragging = true
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    dragPositionMs?.let { viewModel.seekTo(it.toLong()) }
                    viewModel.isSeekDragging = false
                    dragPositionMs = null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = viewModel::skipNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}
