package com.ventus.sys.domain

import com.ventus.sys.domain.model.ScoreDeltas
import com.ventus.sys.domain.model.ScoreResult
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import com.ventus.sys.domain.model.Verdict
import kotlin.math.abs

/**
 * Ports score_features() exactly — app.py:502-532. Weighted mean distance
 * across up to 7 axes, doubled and subtracted from 100. Also mirrored
 * independently in app.js's calcScore() (app.js:87-107) for instant
 * client-side list scoring on desktop — this is the one true Kotlin
 * implementation both the live scorer AND every list screen (Vault,
 * Dashboard KPIs, etc.) call into, replacing both.
 *
 * Key/mode is NOT used in scoring at all — display-only, via [Camelot].
 */
object ScoreEngine {
    private const val LOUDNESS_FLOOR_DB = -30.0
    private const val LOUDNESS_RANGE_DB = 30.0
    private const val BPM_TOLERANCE = 40.0
    private const val NORMALIZED_MAX = 100.0
    private const val NORMALIZED_MIN = 0.0

    // Axis weights — app.py:517-519. Acousticness/instrumentalness are
    // conditional (only included, both in the weight sum AND the distance,
    // when the track's own value is >= this threshold — app.py:508-509).
    private const val WEIGHT_ENERGY = 1.00
    private const val WEIGHT_VALENCE = 1.00
    private const val WEIGHT_DANCE = 0.85
    private const val WEIGHT_LOUDNESS = 0.60
    private const val WEIGHT_BPM = 0.45
    private const val WEIGHT_ACOUSTIC = 0.70
    private const val WEIGHT_INSTRUMENTAL = 0.70
    private const val CONDITIONAL_AXIS_THRESHOLD = 2.0

    private const val SCORE_MAX = 100.0
    private const val SCORE_DISTANCE_MULTIPLIER = 2.0

    private const val CORE_THRESHOLD = 85
    private const val ALIGNED_THRESHOLD = 65
    private const val FRINGE_THRESHOLD = 45
    private const val OUTLIER_THRESHOLD = 25

    private const val DELTA_DECIMALS = 1

    private fun normalizeLoudness(db: Double): Double =
        ((db - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB * NORMALIZED_MAX)
            .coerceIn(NORMALIZED_MIN, NORMALIZED_MAX)

    private fun normalizeBpm(
        bpm: Double,
        ref: Double,
    ): Double = (abs(bpm - ref) / BPM_TOLERANCE * NORMALIZED_MAX).coerceAtMost(NORMALIZED_MAX)

    /**
     * @param profile null/empty means no taste profile exists yet, matching
     * app.py:504-505's `if not p: return 0, "NO SIGNAL", {}` — a distinct
     * sentinel verdict, not reachable via the numeric thresholds below.
     */
    fun score(
        data: TrackFeatures,
        profile: TasteProfile?,
    ): ScoreResult {
        if (profile == null) {
            return ScoreResult(
                score = 0,
                verdict = Verdict.NO_SIGNAL,
                deltas = ScoreDeltas(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            )
        }

        val useAcoustic = data.acousticness >= CONDITIONAL_AXIS_THRESHOLD
        val useInstrumental = data.instrumentalness >= CONDITIONAL_AXIS_THRESHOLD

        val deltaEnergy = abs(data.energy - profile.energy)
        val deltaValence = abs(data.valence - profile.valence)
        val deltaDance = abs(data.danceability - profile.dance)
        val deltaLoudness = abs(normalizeLoudness(data.loudness) - normalizeLoudness(profile.loudness))
        val deltaBpm = normalizeBpm(data.bpm, profile.bpm)
        val deltaAcoustic = if (useAcoustic) abs(data.acousticness - profile.acousticness) else 0.0
        val deltaInstrumental = if (useInstrumental) abs(data.instrumentalness - profile.instrumentalness) else 0.0

        val axes =
            buildList {
                add(deltaEnergy to WEIGHT_ENERGY)
                add(deltaValence to WEIGHT_VALENCE)
                add(deltaDance to WEIGHT_DANCE)
                add(deltaLoudness to WEIGHT_LOUDNESS)
                add(deltaBpm to WEIGHT_BPM)
                if (useAcoustic) add(deltaAcoustic to WEIGHT_ACOUSTIC)
                if (useInstrumental) add(deltaInstrumental to WEIGHT_INSTRUMENTAL)
            }
        val totalWeight = axes.sumOf { it.second }
        val weightedMeanDistance = axes.sumOf { (delta, weight) -> delta * weight } / totalWeight

        val rawScore = SCORE_MAX - (weightedMeanDistance * SCORE_DISTANCE_MULTIPLIER)
        val score = PythonRound.toInt(rawScore).coerceIn(0, SCORE_MAX.toInt())

        val verdict =
            when {
                score >= CORE_THRESHOLD -> Verdict.CORE
                score >= ALIGNED_THRESHOLD -> Verdict.ALIGNED
                score >= FRINGE_THRESHOLD -> Verdict.FRINGE
                score >= OUTLIER_THRESHOLD -> Verdict.OUTLIER
                else -> Verdict.NO_MATCH
            }

        return ScoreResult(
            score = score,
            verdict = verdict,
            deltas =
                ScoreDeltas(
                    energy = PythonRound.toDecimals(deltaEnergy, DELTA_DECIMALS),
                    valence = PythonRound.toDecimals(deltaValence, DELTA_DECIMALS),
                    dance = PythonRound.toDecimals(deltaDance, DELTA_DECIMALS),
                    acoustic = PythonRound.toDecimals(deltaAcoustic, DELTA_DECIMALS),
                    instrumental = PythonRound.toDecimals(deltaInstrumental, DELTA_DECIMALS),
                    loudness = PythonRound.toDecimals(deltaLoudness, DELTA_DECIMALS),
                    bpm = PythonRound.toDecimals(deltaBpm, DELTA_DECIMALS),
                ),
        )
    }
}
