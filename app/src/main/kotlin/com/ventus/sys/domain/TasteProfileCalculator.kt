package com.ventus.sys.domain

import com.ventus.sys.domain.model.TasteProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ports get_taste_profile() exactly — app.py:401-448. Recency-weighted mean
 * per metric, not a simple average.
 *
 * Desktop reads this straight out of a CSV with pandas, including per-column
 * masking for metrics some rows don't have a value for at all (mixed-source
 * CSV rows). Android's vault always carries a real resolved value for every
 * metric on every row (ReccoBeats resolves all 7 at once, or the row isn't
 * inserted at all — see the vault upsert logic), so there's no equivalent
 * per-metric "some tracks are missing just this one field" case to mask
 * around; the weighted mean is computed over every row's real value.
 * [FALLBACK] still applies the same way desktop's does: an empty profile
 * (no rows at all) returns the same fallback constants app.py's `wm()`
 * default args use, not a wrongly-populated zero profile.
 */
object TasteProfileCalculator {
    // Recency weight buckets — app.py:419-422's `w(dt)` closure.
    private const val RECENT_DAYS = 30
    private const val RECENT_WEIGHT = 3.0
    private const val MID_DAYS = 180
    private const val MID_WEIGHT = 1.5
    private const val OLD_DAYS = 365
    private const val OLD_WEIGHT = 1.0
    private const val STALE_WEIGHT = 0.5

    private const val SCALE_THRESHOLD = 1.0
    private const val SCALE_FACTOR = 100.0
    private const val ROUND_DECIMALS = 2

    data class ProfileInputRow(
        val energy: Double,
        val valence: Double,
        val danceability: Double,
        val bpm: Double,
        val acousticness: Double,
        val instrumentalness: Double,
        val loudness: Double,
        val addedAt: LocalDate,
    )

    private fun recencyWeight(
        addedAt: LocalDate,
        today: LocalDate,
    ): Double {
        val daysSinceAdded = ChronoUnit.DAYS.between(addedAt, today)
        return when {
            daysSinceAdded <= RECENT_DAYS -> RECENT_WEIGHT
            daysSinceAdded <= MID_DAYS -> MID_WEIGHT
            daysSinceAdded <= OLD_DAYS -> OLD_WEIGHT
            else -> STALE_WEIGHT
        }
    }

    fun calculate(
        rows: List<ProfileInputRow>,
        today: LocalDate = LocalDate.now(),
    ): TasteProfile {
        if (rows.isEmpty()) return TasteProfile.FALLBACK

        val weights = rows.map { recencyWeight(it.addedAt, today) }
        val totalWeight = weights.sum()

        fun weightedMean(selector: (ProfileInputRow) -> Double): Double =
            rows.indices.sumOf { i -> selector(rows[i]) * weights[i] } / totalWeight

        // app.py:433's `m if m>1 else m*100` — rescale only if the weighted
        // mean itself looks like a 0-1 fraction, leave as-is otherwise.
        fun weightedMeanScaled(selector: (ProfileInputRow) -> Double): Double {
            val mean = weightedMean(selector)
            val scaled = if (mean > SCALE_THRESHOLD) mean else mean * SCALE_FACTOR
            return PythonRound.toDecimals(scaled, ROUND_DECIMALS)
        }

        fun weightedMeanRaw(selector: (ProfileInputRow) -> Double): Double = PythonRound.toDecimals(weightedMean(selector), ROUND_DECIMALS)

        return TasteProfile(
            energy = weightedMeanScaled { it.energy },
            valence = weightedMeanScaled { it.valence },
            dance = weightedMeanScaled { it.danceability },
            bpm = weightedMeanRaw { it.bpm },
            acousticness = weightedMeanScaled { it.acousticness },
            instrumentalness = weightedMeanScaled { it.instrumentalness },
            loudness = weightedMeanRaw { it.loudness },
        )
    }
}
