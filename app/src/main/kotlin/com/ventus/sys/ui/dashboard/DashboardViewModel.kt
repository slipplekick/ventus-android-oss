package com.ventus.sys.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.data.repository.VaultRepository
import com.ventus.sys.domain.ScoreEngine
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import com.ventus.sys.domain.model.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

private const val HISTOGRAM_BUCKETS = 10
private const val HISTOGRAM_MIN = 0.0
private const val HISTOGRAM_MAX = 100.0
private const val PERCENT_MULTIPLIER = 100
private const val STOP_TIMEOUT_MS = 5000L

data class DashboardUiState(
    val totalTracks: Int = 0,
    val avgEnergy: Int = 0,
    val avgValence: Int = 0,
    val avgBpm: Int = 0,
    val coreCount: Int = 0,
    val alignedCount: Int = 0,
    val vaultIndexed: Int = 0,
    val coveragePercent: Int = 0,
    val energyHistogram: List<Int> = List(HISTOGRAM_BUCKETS) { 0 },
    val valenceHistogram: List<Int> = List(HISTOGRAM_BUCKETS) { 0 },
    val profile: TasteProfile = TasteProfile.FALLBACK,
)

/**
 * Ports app.js's loadData() dashboard section (app.js:303-378): KPI grid,
 * energy/valence histograms, the 7-axis profile radar, and per-axis
 * profile-stat bars. Genre DNA (app.js's dnaGrid, fed by a Last.fm
 * genre-tag scan against /api/vibe_dna) is NOT included here - that's a
 * separate network integration (Last.fm's API, not ReccoBeats/Spotify)
 * needing its own API key that isn't provisioned for Android, a genuinely
 * separate scope decision from anything already made about this app.
 *
 * "VAULT INDEXED"/"COVERAGE" intersect the taste-profile tracks with the
 * vault, not the vault's raw size — matches app.js's own explicit fix for
 * this (app.js:333-339's comment: the vault accumulates across every
 * playlist ever synced, so dividing by the *current* profile's total can
 * exceed 100% if a smaller playlist replaces a larger one).
 */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        tasteProfileRepository: TasteProfileRepository,
        vaultRepository: VaultRepository,
    ) : ViewModel() {
        val uiState: StateFlow<DashboardUiState> =
            combine(
                tasteProfileRepository.observeTrackFeatures(),
                tasteProfileRepository.observeProfile(),
                vaultRepository.observeAll(),
            ) { tracks, profile, vaultFeatures ->
                buildState(tracks, profile, vaultFeatures)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

        private fun buildState(
            tracks: List<TrackFeatures>,
            profile: TasteProfile,
            vaultFeatures: List<TrackFeatures>,
        ): DashboardUiState {
            val total = tracks.size
            val vaultIds = vaultFeatures.mapTo(HashSet()) { it.id }
            val vaultIndexed = tracks.count { it.id in vaultIds }

            var coreCount = 0
            var alignedCount = 0
            tracks.forEach { track ->
                when (ScoreEngine.score(track, profile).verdict) {
                    Verdict.CORE -> coreCount++
                    Verdict.ALIGNED -> alignedCount++
                    else -> Unit
                }
            }

            return DashboardUiState(
                totalTracks = total,
                avgEnergy = average(tracks) { it.energy },
                avgValence = average(tracks) { it.valence },
                avgBpm = average(tracks) { it.bpm },
                coreCount = coreCount,
                alignedCount = alignedCount,
                vaultIndexed = vaultIndexed,
                coveragePercent = if (total > 0) vaultIndexed * PERCENT_MULTIPLIER / total else 0,
                energyHistogram = histogram(tracks) { it.energy },
                valenceHistogram = histogram(tracks) { it.valence },
                profile = profile,
            )
        }

        private fun average(
            tracks: List<TrackFeatures>,
            selector: (TrackFeatures) -> Double,
        ): Int = if (tracks.isEmpty()) 0 else (tracks.sumOf(selector) / tracks.size).roundToInt()

        /** Ports app.js's buildHistogram (app.js:283-301): fixed bucket count over [min, max]. */
        private fun histogram(
            tracks: List<TrackFeatures>,
            selector: (TrackFeatures) -> Double,
        ): List<Int> {
            val counts = MutableList(HISTOGRAM_BUCKETS) { 0 }
            val step = (HISTOGRAM_MAX - HISTOGRAM_MIN) / HISTOGRAM_BUCKETS
            tracks.forEach { track ->
                val bucket = ((selector(track) - HISTOGRAM_MIN) / step).toInt().coerceIn(0, HISTOGRAM_BUCKETS - 1)
                counts[bucket]++
            }
            return counts
        }
    }
