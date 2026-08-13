package com.ventus.sys.ui.scorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ventus.sys.domain.model.ScoreDeltas
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import com.ventus.sys.domain.model.Verdict
import com.ventus.sys.service.NowPlayingState
import com.ventus.sys.ui.common.verdictColor

private const val LOUDNESS_FLOOR_DB = -30f
private const val LOUDNESS_RANGE_DB = 30f
private const val BPM_NORM_RANGE = 200f

/** Ports app.js's Live Scorer page (app.js:912-1114, 7-axis radar + score + compare bars). */
@Composable
fun LiveScorerScreen(viewModel: LiveScorerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            // Keyed on whether a track is loaded at all, not isPlaying — pausing
            // (from the transport bar or the real Spotify app) shouldn't wipe
            // the radar/score view, only "nothing loaded" should.
            state.trackId == null -> IdleState()

            state.isScoring -> ScoringState(state)

            // isScoring flips false once resolution finishes either way -
            // scoreResult == null here means it finished and found nothing
            // (ReccoBeats has no match for this track), not "still working."
            // Previously this fell through to ScoringState too, which meant
            // any track ReccoBeats can't resolve just spun on "Analyzing…"
            // for the rest of its runtime (a fast 200 response with no
            // match, not a slow/stuck request) - looks exactly like a
            // hung/broken app rather than an honest "no data."
            // Desktop's own live scorer shows a real ERR/NO MATCH state for
            // this same case (app.js's triggerScore, app.js:955-962) - this
            // matches that instead of a silent infinite spinner.
            state.scoreResult == null -> NoDataState(state)

            else -> ScoredState(state)
        }
    }
}

@Composable
private fun IdleState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "// STANDBY", style = MaterialTheme.typography.titleMedium)
        Text(text = "Play something on Spotify", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScoringState(state: NowPlayingState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TrackHeader(state)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
        Text(text = "Analyzing…", modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Ports app.js's ERR state (triggerScore, app.js:955-962) — a real terminal
 * state, not a spinner that never resolves. Labeled "NO DATA", not "NO
 * MATCH" — [Verdict.NO_MATCH] is a real, distinct verdict for a track that
 * *was* scored and just scores very low (see ScoredState), and reusing the
 * same words for "we never got data to score at all" would make a
 * screenshot of either state impossible to tell apart at a glance.
 */
@Composable
private fun NoDataState(state: NowPlayingState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TrackHeader(state)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "NO DATA",
            style = MaterialTheme.typography.titleMedium,
            color = verdictColor(Verdict.NO_MATCH),
        )
        Text(
            text = "No audio data available for this track",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "Try skipping to another track",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ScoredState(state: NowPlayingState) {
    val result = state.scoreResult ?: return
    val features = state.features
    val profile = state.profile

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackHeader(state)
        Spacer(modifier = Modifier.height(12.dp))

        if (features != null && profile != null) {
            RadarChart(
                profileValues = toRadarValues(profile),
                trackValues = toRadarValues(features),
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${result.score}%",
            style = MaterialTheme.typography.displayMedium,
            color = verdictColor(result.verdict),
        )
        Text(
            text = result.verdict.display,
            style = MaterialTheme.typography.titleMedium,
            color = verdictColor(result.verdict),
        )

        if (features != null && profile != null) {
            Spacer(modifier = Modifier.height(16.dp))
            CompareBars(features, profile, result.deltas)
        }
    }
}

@Composable
private fun TrackHeader(state: NowPlayingState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.albumArtUrl != null) {
            AsyncImage(
                model = state.albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).padding(end = 12.dp),
            )
        }
        Column {
            Text(text = state.trackName.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(text = state.artist.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class CompareRow(
    val label: String,
    val trackValue: Float,
    val profileValue: Float,
    val delta: Double,
)

@Composable
private fun CompareBars(
    features: TrackFeatures,
    profile: TasteProfile,
    deltas: ScoreDeltas,
) {
    val rows =
        listOf(
            CompareRow("ENERGY", features.energy.toFloat(), profile.energy.toFloat(), deltas.energy),
            CompareRow("VALENCE", features.valence.toFloat(), profile.valence.toFloat(), deltas.valence),
            CompareRow("DANCE", features.danceability.toFloat(), profile.dance.toFloat(), deltas.dance),
            CompareRow("ACOUSTIC", features.acousticness.toFloat(), profile.acousticness.toFloat(), deltas.acoustic),
            CompareRow("INSTRUMENTAL", features.instrumentalness.toFloat(), profile.instrumentalness.toFloat(), deltas.instrumental),
            CompareRow(
                "LOUDNESS",
                normalizeLoudness(features.loudness.toFloat()) * 100f,
                normalizeLoudness(profile.loudness.toFloat()) * 100f,
                deltas.loudness,
            ),
            CompareRow(
                "BPM",
                normalizeBpm(features.bpm.toFloat()) * 100f,
                normalizeBpm(profile.bpm.toFloat()) * 100f,
                deltas.bpm,
            ),
        )
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row -> CompareBarRow(row) }
    }
}

@Composable
private fun CompareBarRow(row: CompareRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = row.label, style = MaterialTheme.typography.labelSmall)
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.fillMaxWidth((row.profileValue / 100f).coerceIn(0f, 1f)).fillMaxSize(),
            ) {}
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            Surface(
                color = deltaColor(row.delta),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.fillMaxWidth((row.trackValue / 100f).coerceIn(0f, 1f)).fillMaxSize(),
            ) {}
        }
    }
}

private fun normalizeLoudness(db: Float): Float = ((db - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB).coerceIn(0f, 1f)

private fun normalizeBpm(bpm: Float): Float = (bpm / BPM_NORM_RANGE).coerceAtMost(1f)

/** Order matches RadarChart's AXIS_LABELS: NRG, VAL, DNC, LOUD, BPM, ACST, INST (app.js:241's SR_AXES). */
private fun toRadarValues(features: TrackFeatures): FloatArray =
    floatArrayOf(
        (features.energy / 100.0).toFloat(),
        (features.valence / 100.0).toFloat(),
        (features.danceability / 100.0).toFloat(),
        normalizeLoudness(features.loudness.toFloat()),
        normalizeBpm(features.bpm.toFloat()),
        (features.acousticness / 100.0).toFloat(),
        (features.instrumentalness / 100.0).toFloat(),
    )

private fun toRadarValues(profile: TasteProfile): FloatArray =
    floatArrayOf(
        (profile.energy / 100.0).toFloat(),
        (profile.valence / 100.0).toFloat(),
        (profile.dance / 100.0).toFloat(),
        normalizeLoudness(profile.loudness.toFloat()),
        normalizeBpm(profile.bpm.toFloat()),
        (profile.acousticness / 100.0).toFloat(),
        (profile.instrumentalness / 100.0).toFloat(),
    )

private const val DELTA_GOOD_THRESHOLD = 15.0
private const val DELTA_MED_THRESHOLD = 30.0

/** Ports app.js's deltaClass()/barClass() thresholds (app.js:134-144). */
private fun deltaColor(delta: Double): Color =
    when {
        delta <= DELTA_GOOD_THRESHOLD -> Color(0xFF4CAF50)
        delta <= DELTA_MED_THRESHOLD -> Color(0xFFFFC107)
        else -> Color(0xFFFF4C2B)
    }
