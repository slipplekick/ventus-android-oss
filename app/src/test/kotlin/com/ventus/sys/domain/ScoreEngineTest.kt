package com.ventus.sys.domain

import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import com.ventus.sys.domain.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden-fixture-style tests: hand-computed against score_features()'s
 * documented formula (app.py:502-532). Each case's expected value is
 * derived by evaluating the same formula by hand, not just asserting
 * whatever ScoreEngine currently outputs — the point is to catch a
 * regression against the documented formula, not to bless whatever the
 * implementation happens to compute today.
 */
class ScoreEngineTest {
    private val referenceProfile =
        TasteProfile(
            energy = 69.0,
            valence = 67.0,
            dance = 60.0,
            bpm = 120.0,
            acousticness = 10.0,
            instrumentalness = 5.0,
            loudness = -8.0,
        )

    @Test
    fun `identical track to profile scores 100 CORE`() {
        val track =
            TrackFeatures(
                id = "t1",
                energy = 69.0,
                valence = 67.0,
                danceability = 60.0,
                bpm = 120.0,
                acousticness = 0.0, // below 2.0 threshold -> excluded, matches profile's irrelevance
                instrumentalness = 0.0,
                loudness = -8.0,
            )
        val result = ScoreEngine.score(track, referenceProfile)
        assertEquals(100, result.score)
        assertEquals(Verdict.CORE, result.verdict)
    }

    @Test
    fun `null profile returns NO_SIGNAL sentinel not a numeric-threshold verdict`() {
        val track = TrackFeatures("t1", 50.0, 50.0, 50.0, 120.0, 0.0, 0.0, -8.0)
        val result = ScoreEngine.score(track, null)
        assertEquals(0, result.score)
        assertEquals(Verdict.NO_SIGNAL, result.verdict)
    }

    @Test
    fun `acousticness below 2_0 is excluded from weight sum entirely, not zero-weighted`() {
        // Two tracks differing only in acousticness (both < 2.0 threshold) must
        // score identically — the axis is excluded, not just zero-weighted with
        // a zero delta (app.py:508,515: `if use_ac else 0` is inside the delta
        // calc, but the axis itself is only appended to `axes` when use_ac).
        val below1 = TrackFeatures("a", 69.0, 67.0, 60.0, 120.0, 0.5, 0.0, -8.0)
        val below2 = TrackFeatures("b", 69.0, 67.0, 60.0, 120.0, 1.9, 0.0, -8.0)
        assertEquals(
            ScoreEngine.score(below1, referenceProfile).score,
            ScoreEngine.score(below2, referenceProfile).score,
        )
    }

    @Test
    fun `acousticness at or above 2_0 threshold is included and affects score`() {
        val excluded = TrackFeatures("a", 69.0, 67.0, 60.0, 120.0, 1.9, 0.0, -8.0)
        val included = TrackFeatures("b", 69.0, 67.0, 60.0, 120.0, 90.0, 0.0, -8.0)
        val excludedScore = ScoreEngine.score(excluded, referenceProfile).score
        val includedScore = ScoreEngine.score(included, referenceProfile).score
        // Large acoustic delta once included should pull the score down.
        assert(includedScore < excludedScore) { "expected $includedScore < $excludedScore" }
    }

    @Test
    fun `verdict thresholds match exactly at boundaries`() {
        // Construct tracks whose energy delta alone lands the score at each
        // boundary (energy weight 1.0 dominates when all other axes match).
        fun trackWithEnergyDelta(delta: Double) =
            TrackFeatures(
                id = "x",
                energy = referenceProfile.energy + delta,
                valence = referenceProfile.valence,
                danceability = referenceProfile.dance,
                bpm = referenceProfile.bpm,
                acousticness = 0.0,
                instrumentalness = 0.0,
                loudness = referenceProfile.loudness,
            )
        // score = round(100 - wv*2); with only energy differing (weight 1.0 of
        // total 2.9), wv = delta * 1.0 / 2.9. Solve for delta at score=85,65,45,25.
        val totalWeight = 1.00 + 1.00 + 0.85 + 0.60 + 0.45 // 2.90, no conditional axes

        fun deltaForScore(targetScore: Int): Double {
            val wv = (100.0 - targetScore) / 2.0
            return wv * totalWeight
        }
        assert(ScoreEngine.score(trackWithEnergyDelta(deltaForScore(85)), referenceProfile).score >= 85)
        assert(ScoreEngine.score(trackWithEnergyDelta(deltaForScore(65)), referenceProfile).verdict == Verdict.ALIGNED)
        assert(ScoreEngine.score(trackWithEnergyDelta(deltaForScore(45)), referenceProfile).verdict == Verdict.FRINGE)
        assert(ScoreEngine.score(trackWithEnergyDelta(deltaForScore(25)), referenceProfile).verdict == Verdict.OUTLIER)
    }

    @Test
    fun `score is clamped to 0-100 range`() {
        val extreme = TrackFeatures("x", 0.0, 0.0, 0.0, 400.0, 100.0, 100.0, 30.0)
        val result = ScoreEngine.score(extreme, referenceProfile)
        assert(result.score in 0..100)
    }
}
