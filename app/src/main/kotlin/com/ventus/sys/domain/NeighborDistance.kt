package com.ventus.sys.domain

import com.ventus.sys.domain.model.TrackFeatures
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Ports the Discover screen's "closest matches in your index" distance
 * function exactly — app.js:672-696 (renderNeighbors). This is a SEPARATE
 * algorithm from [ScoreEngine], not a reuse of it, and the difference is
 * deliberate, not a bug to "fix" during the port:
 *
 * - Weighted Euclidean distance (weight applied before squaring), not
 *   ScoreEngine's weighted mean of raw deltas.
 * - Acousticness/instrumentalness are ALWAYS included, unconditionally —
 *   unlike ScoreEngine's >=2.0 conditional inclusion (app.py:508-509).
 * - Lower distance = closer match; there's no 0-100 "score" or verdict,
 *   just a ranking (Discover sorts ascending and takes the top 8).
 */
object NeighborDistance {
    private const val LOUDNESS_FLOOR_DB = -30.0
    private const val LOUDNESS_RANGE_DB = 30.0
    private const val LOUDNESS_NORM_MAX = 100.0
    private const val BPM_NORM_RANGE = 200.0
    private const val BPM_NORM_MAX = 100.0

    private const val WEIGHT_ENERGY = 1.00
    private const val WEIGHT_VALENCE = 1.00
    private const val WEIGHT_DANCE = 0.85
    private const val WEIGHT_ACOUSTIC = 0.70
    private const val WEIGHT_INSTRUMENTAL = 0.70
    private const val WEIGHT_LOUDNESS = 0.60
    private const val WEIGHT_BPM = 0.45

    private const val SQUARE_EXPONENT = 2.0

    private fun normalizeLoudness(db: Double): Double =
        ((db - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB * LOUDNESS_NORM_MAX)
            .coerceIn(0.0, LOUDNESS_NORM_MAX)

    private fun normalizeBpm(bpm: Double): Double = (bpm / BPM_NORM_RANGE * BPM_NORM_MAX).coerceAtMost(BPM_NORM_MAX)

    /** Lower = closer match. `candidate` is a library track; `target` is the reference (searched/analyzed) track. */
    fun distance(
        candidate: TrackFeatures,
        target: TrackFeatures,
    ): Double {
        val sumOfSquares =
            ((candidate.energy - target.energy) * WEIGHT_ENERGY).pow(SQUARE_EXPONENT) +
                ((candidate.valence - target.valence) * WEIGHT_VALENCE).pow(SQUARE_EXPONENT) +
                ((candidate.danceability - target.danceability) * WEIGHT_DANCE).pow(SQUARE_EXPONENT) +
                ((candidate.acousticness - target.acousticness) * WEIGHT_ACOUSTIC).pow(SQUARE_EXPONENT) +
                ((candidate.instrumentalness - target.instrumentalness) * WEIGHT_INSTRUMENTAL).pow(SQUARE_EXPONENT) +
                (
                    (normalizeLoudness(candidate.loudness) - normalizeLoudness(target.loudness)) *
                        WEIGHT_LOUDNESS
                ).pow(SQUARE_EXPONENT) +
                ((normalizeBpm(candidate.bpm) - normalizeBpm(target.bpm)) * WEIGHT_BPM).pow(SQUARE_EXPONENT)
        return sqrt(sumOfSquares)
    }

    /** Returns [candidates] sorted closest-first, matching Discover's ascending sort (app.js:697). */
    fun nearest(
        target: TrackFeatures,
        candidates: List<TrackFeatures>,
        limit: Int = 8,
    ): List<TrackFeatures> = candidates.sortedBy { distance(it, target) }.take(limit)
}
