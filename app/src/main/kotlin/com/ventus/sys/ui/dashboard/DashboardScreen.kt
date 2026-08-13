package com.ventus.sys.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.Verdict
import com.ventus.sys.ui.common.verdictColor
import com.ventus.sys.ui.scorer.RadarChart
import androidx.compose.foundation.layout.Spacer as ComposeSpacer

private const val LOUDNESS_FLOOR_DB = -30f
private const val LOUDNESS_RANGE_DB = 30f
private const val BPM_NORM_RANGE = 200f
private const val KPI_GRID_COLUMNS = 2

/** Ports app.js's dashboard page (loadData, app.js:303-378) — KPIs, histograms, profile radar, per-axis bars, verdict legend. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(text = "SYSTEM // DASHBOARD", style = MaterialTheme.typography.headlineSmall)
        Spacer()

        if (state.totalTracks == 0) {
            Text(
                text = "No taste profile yet — sync a playlist to populate the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        KpiGrid(state)
        Spacer()

        Text(text = "TASTE PROFILE", style = MaterialTheme.typography.titleMedium)
        RadarChart(
            profileValues = toRadarValues(state.profile),
            trackValues = null,
            modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp),
        )

        ProfileStatRows(state.profile)
        Spacer()

        Text(text = "ENERGY DISTRIBUTION", style = MaterialTheme.typography.titleMedium)
        Histogram(state.energyHistogram)
        Spacer()

        Text(text = "VALENCE DISTRIBUTION", style = MaterialTheme.typography.titleMedium)
        Histogram(state.valenceHistogram)
        Spacer()

        VerdictLegend()
        Spacer()

        // Genre DNA (app.js's dnaGrid) deliberately omitted - see DashboardViewModel's
        // doc comment. Honest placeholder rather than silently dropping the section.
        Text(text = "GENRE DNA", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Not yet available on Android — needs a separate genre-tagging integration.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun Spacer() = ComposeSpacer(modifier = Modifier.height(20.dp))

private data class Kpi(
    val label: String,
    val value: String,
    val color: Color? = null,
)

@Composable
private fun KpiGrid(state: DashboardUiState) {
    val kpis =
        listOf(
            Kpi("TOTAL TRACKS", state.totalTracks.toString()),
            Kpi("AVG ENERGY", "${state.avgEnergy}%", MaterialTheme.colorScheme.tertiary),
            Kpi("AVG VALENCE", "${state.avgValence}%", verdictColor(Verdict.CORE)),
            Kpi("CORE", state.coreCount.toString(), verdictColor(Verdict.CORE)),
            Kpi("ALIGNED", state.alignedCount.toString(), verdictColor(Verdict.ALIGNED)),
            Kpi("AVG BPM", state.avgBpm.toString()),
            Kpi("VAULT INDEXED", state.vaultIndexed.toString()),
            Kpi("COVERAGE", "${state.coveragePercent}%"),
        )
    LazyVerticalGrid(
        columns = GridCells.Fixed(KPI_GRID_COLUMNS),
        modifier = Modifier.height(320.dp),
    ) {
        items(kpis) { kpi -> KpiCard(kpi) }
    }
}

@Composable
private fun KpiCard(kpi: Kpi) {
    Surface(
        modifier = Modifier.padding(4.dp),
        shape = RoundedCornerShape(4.dp),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = kpi.label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = kpi.value,
                style = MaterialTheme.typography.headlineSmall,
                color = kpi.color ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class ProfileStat(
    val label: String,
    val display: String,
    val fraction: Float,
)

/** Ports app.js's profileStats rows (app.js:359-375) — one bar per axis, same normalization ScoreEngine/RadarChart use. */
@Composable
private fun ProfileStatRows(profile: TasteProfile) {
    val stats =
        listOf(
            ProfileStat("ENERGY AVG", "${profile.energy.toInt()}%", (profile.energy / 100.0).toFloat()),
            ProfileStat("VALENCE AVG", "${profile.valence.toInt()}%", (profile.valence / 100.0).toFloat()),
            ProfileStat("DANCEABILITY", "${profile.dance.toInt()}%", (profile.dance / 100.0).toFloat()),
            ProfileStat("BPM (NORM)", profile.bpm.toInt().toString(), normalizeBpm(profile.bpm.toFloat())),
            ProfileStat("ACOUSTICNESS", "${profile.acousticness.toInt()}%", (profile.acousticness / 100.0).toFloat()),
            ProfileStat(
                "INSTRUMENTAL",
                "${profile.instrumentalness.toInt()}%",
                (profile.instrumentalness / 100.0).toFloat(),
            ),
            ProfileStat(
                "LOUDNESS AVG",
                "%.1f".format(profile.loudness),
                normalizeLoudness(profile.loudness.toFloat()),
            ),
        )
    Column(modifier = Modifier.fillMaxWidth()) {
        stats.forEach { stat ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stat.label, style = MaterialTheme.typography.labelSmall)
                    Text(text = stat.display, style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { stat.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** Ports app.js's buildHistogram bars (app.js:283-301) as a simple bottom-aligned bar chart. */
@Composable
private fun Histogram(buckets: List<Int>) {
    val peak = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { count ->
            val fraction = count.toFloat() / peak
            Box(
                modifier =
                    Modifier
                        .width(20.dp)
                        .height((96 * fraction).dp.coerceAtLeast(3.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun VerdictLegend() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "VERDICT LEGEND", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        listOf(Verdict.CORE, Verdict.ALIGNED, Verdict.FRINGE, Verdict.OUTLIER, Verdict.NO_MATCH).forEach { verdict ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(12.dp)
                            .height(12.dp)
                            .background(verdictColor(verdict), RoundedCornerShape(2.dp)),
                )
                Text(text = "  ${verdict.display}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun normalizeLoudness(db: Float): Float = ((db - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB).coerceIn(0f, 1f)

private fun normalizeBpm(bpm: Float): Float = (bpm / BPM_NORM_RANGE).coerceAtMost(1f)

/** Order matches RadarChart's AXIS_LABELS: NRG, VAL, DNC, LOUD, BPM, ACST, INST. */
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
