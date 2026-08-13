package com.ventus.sys.service

import com.ventus.sys.domain.model.ScoreResult
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures

/**
 * What's currently playing + its score, if resolved yet. Exposed via
 * [NowPlayingStateHolder] as a StateFlow any screen subscribes to
 * in-process — replaces desktop's SSE broadcast mechanism entirely, since
 * there's no separate backend process here to need an HTTP hop for this.
 *
 * Carries the raw [features]/[profile] snapshot alongside [scoreResult]
 * (not just the deltas) so the Live Scorer UI can render the 7-axis radar
 * and compare bars without a second round-trip back through the
 * repositories — the service already resolved both once.
 */
data class NowPlayingState(
    val isPlaying: Boolean = false,
    val trackId: String? = null,
    val trackName: String? = null,
    val artist: String? = null,
    val albumArtUrl: String? = null,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val features: TrackFeatures? = null,
    val profile: TasteProfile? = null,
    val scoreResult: ScoreResult? = null,
    val isScoring: Boolean = false,
)
