package com.ventus.sys.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ventus.sys.data.remote.dto.UserPlaylistDto

/**
 * Ports app.js's #firstRunOverlay (app.js:545-599): pick a playlist from
 * your library, use Liked Songs instead, paste an ID/URL manually, or
 * skip and sync later. All four paths route through the same sync logic
 * (OnboardingViewModel.sync) — the desktop UI keeps Liked Songs visually
 * separate from the playlist list on purpose (app.js:577-580's own
 * comment: reads as an obviously-skippable alternative, not a required
 * step), kept here.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
) {
    val step by viewModel.step.collectAsState()

    // LaunchedEffect, not a direct call in the composable body - onDone()
    // mutates MainActivity's isOnboarded MutableState, and writing to an
    // ancestor's state during this composable's own composition is exactly
    // what Compose's side-effect-free-composition contract forbids. It
    // "worked" before only because the write is idempotent and the parent
    // unmounts this screen the next frame - not a guarantee that survives
    // e.g. wrapping this in an animated container later.
    LaunchedEffect(step) {
        if (step is OnboardingStep.Done) onDone()
    }

    // safeDrawingPadding(), not just a flat 20dp - this screen renders
    // before HomeScreen's Scaffold exists (no TopAppBar/bottomBar to absorb
    // the status/nav-bar insets the way every post-onboarding screen gets
    // for free), and MainActivity calls enableEdgeToEdge(). Without it the
    // title draws right up against the status bar and Skip/manual-entry
    // controls crowd the nav bar.
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        Text(text = "Choose Your Taste Profile Playlist", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "VENTUS learns your taste from a playlist you already love.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        when (val s = step) {
            is OnboardingStep.LoadingPlaylists, is OnboardingStep.Syncing -> {
                LoadingState(
                    message = if (s is OnboardingStep.Syncing) "Syncing — learning your taste profile…" else "Loading your playlists…",
                )
            }

            is OnboardingStep.LoadFailed -> {
                ErrorState(s.message, onRetry = viewModel::loadPlaylists)
            }

            is OnboardingStep.SyncFailed -> {
                ErrorState(s.message, onRetry = viewModel::loadPlaylists)
            }

            is OnboardingStep.PickerReady -> {
                PlaylistPicker(
                    playlists = s.playlists,
                    onSelect = { viewModel.selectPlaylist(it.id) },
                    onUseLikedSongs = viewModel::useLikedSongs,
                    onManualConfirm = viewModel::confirmManualInput,
                    onSkip = viewModel::skip,
                )
            }

            is OnboardingStep.Done -> {
                Unit
            } // handled above
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = message, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "// $message", color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
    }
}

@Composable
private fun PlaylistPicker(
    playlists: List<UserPlaylistDto>,
    onSelect: (UserPlaylistDto) -> Unit,
    onUseLikedSongs: () -> Unit,
    onManualConfirm: (String) -> Unit,
    onSkip: () -> Unit,
) {
    var manualInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (playlists.isEmpty()) {
                item { Text("No playlists found — use the manual ID box below.", modifier = Modifier.padding(vertical = 24.dp)) }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist, onClick = { onSelect(playlist) })
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Paste playlist ID or URL") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onManualConfirm(manualInput) }) { Text("Use") }
        }

        OutlinedButton(
            onClick = onUseLikedSongs,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("♥ Use My Liked Songs Instead") }

        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Skip — I'll sync manually")
        }
    }
}

private val PLAYLIST_COVER_SIZE = 38.dp

@Composable
private fun PlaylistRow(
    playlist: UserPlaylistDto,
    onClick: () -> Unit,
) {
    // Explicit Color.Transparent - same fix as every other row Surface in
    // the app: no color param defaults to MaterialTheme.colorScheme.surface,
    // a visibly different shade from the actual screen background. Click
    // ripple/indication still works fine on a transparent Surface.
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Color.Transparent) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(playlist.coverUrl)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        "${playlist.trackCount} tracks" +
                            (playlist.owner.displayName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(text = "SELECT ⟶", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Ports app.js's 38x38 cover-or-note-glyph placeholder (renderPlaylistPicker, app.js:2363-2367). */
@Composable
private fun PlaylistCover(url: String?) {
    Box(
        modifier =
            Modifier
                .size(PLAYLIST_COVER_SIZE)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PLAYLIST_COVER_SIZE),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
