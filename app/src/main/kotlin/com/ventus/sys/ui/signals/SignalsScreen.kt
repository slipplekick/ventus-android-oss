package com.ventus.sys.ui.signals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ventus.sys.ui.common.verdictColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val PLAYED_AT_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")

/** Ports app.js's Signals page (loadSignals, app.js:1916-2043) — top tracks/artists + recently-played with a ⚡80%+ badge. */
@Composable
fun SignalsScreen(viewModel: SignalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        RangeSelector(state.range, viewModel::setRange)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { SectionHeader("RECENTLY PLAYED") }
            item { RecentSection(state.recent) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { SectionHeader("TOP TRACKS") }
            item { TopTracksSection(state.tracks) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { SectionHeader("TOP ARTISTS") }
            item { TopArtistsSection(state.artists) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RangeSelector(
    selected: SignalsRange,
    onSelect: (SignalsRange) -> Unit,
) {
    // Horizontally scrollable, not a plain fillMaxWidth() Row - three chips
    // ("LAST 4 WEEKS", "LAST 6 MONTHS", "ALL TIME") don't reliably fit one
    // screen width at normal font scale, and a plain Row with no weight/
    // scroll handling squeezes whichever chip runs out of room down to
    // whatever's left, wrapping its label one letter per line instead of
    // just scrolling into view.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        SignalsRange.entries.forEachIndexed { index, range ->
            if (index > 0) Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> SectionStatus(state: SignalsSectionState<T>): Boolean {
    when (state) {
        is SignalsSectionState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return false
        }

        is SignalsSectionState.Error -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return false
        }

        is SignalsSectionState.Loaded -> {
            if (state.items.isEmpty()) {
                Text(
                    text = "No data for this range.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return false
            }
        }
    }
    return true
}

@Composable
private fun RecentSection(state: SignalsSectionState<RecentTrackUiItem>) {
    if (!SectionStatus(state) || state !is SignalsSectionState.Loaded) return
    Column {
        state.items.forEach { track -> RecentRow(track) }
    }
}

@Composable
private fun RecentRow(track: RecentTrackUiItem) {
    // Transparent, not MaterialTheme.colorScheme.surface, for the
    // non-highlighted case - surface is a visibly different (lighter,
    // blue-tinted) shade from the screen's actual background, and every
    // row got wrapped in this Surface regardless of isHighScore, not just
    // the ones meant to stand out. That painted a mismatched-color box
    // behind every single row instead of leaving ordinary rows blending
    // into the background the way TopTracksSection/TopArtistsSection's
    // plain (non-Surface-wrapped) rows already correctly do.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (track.isHighScore) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(track.albumArtUrl, size = 44.dp, shape = RoundedCornerShape(4.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(text = track.name, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${track.artist} · ${formatPlayedAt(track.playedAt)}", style = MaterialTheme.typography.bodySmall)
            }
            val scoreText = track.score?.let { "${if (track.isHighScore) "⚡ " else ""}$it%" } ?: "—"
            Text(
                text = scoreText,
                style = MaterialTheme.typography.bodyMedium,
                color = track.verdict?.let { verdictColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun TopTracksSection(state: SignalsSectionState<TopTrackUiItem>) {
    if (!SectionStatus(state) || state !is SignalsSectionState.Loaded) return
    Column {
        state.items.forEach { track -> TopTrackRow(track) }
    }
}

@Composable
private fun TopTrackRow(track: TopTrackUiItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(track.rank)
        CoverArt(track.albumArtUrl, size = 44.dp, shape = RoundedCornerShape(4.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(text = track.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = track.artist, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun TopArtistsSection(state: SignalsSectionState<TopArtistUiItem>) {
    if (!SectionStatus(state) || state !is SignalsSectionState.Loaded) return
    Column {
        state.items.forEach { artist -> TopArtistRow(artist) }
    }
}

@Composable
private fun TopArtistRow(artist: TopArtistUiItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(artist.rank)
        CoverArt(artist.imageUrl, size = 44.dp, shape = CircleShape)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = artist.genres.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatFollowers(artist.followers), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun RankBadge(rank: Int) {
    Text(
        text = "$rank",
        style = MaterialTheme.typography.bodyMedium,
        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(28.dp),
    )
}

@Composable
private fun CoverArt(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "♪", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatFollowers(count: Long?): String =
    when {
        count == null -> "—"
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }

private fun formatPlayedAt(iso: String): String =
    try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(PLAYED_AT_FORMAT)
    } catch (e: DateTimeParseException) {
        ""
    }
