package com.ventus.sys.domain.model

/**
 * Recency-weighted mean taste profile — mirrors `get_taste_profile()`'s
 * return shape (app.py:401-448). Field name `dance` (not `danceability`)
 * matches the Python dict key exactly, since ScoreEngine reads `p.dance`
 * and getting this wrong is exactly the kind of silent-mismatch bug a
 * faithful port needs to avoid.
 */
data class TasteProfile(
    val energy: Double,
    val valence: Double,
    val dance: Double,
    val bpm: Double,
    val acousticness: Double,
    val instrumentalness: Double,
    val loudness: Double,
) {
    companion object {
        // Fallback constants when a metric has no data at all — app.py:58-59
        // (get_taste_profile's `wm()` fb= defaults) and app.py:447-448
        // (the malformed-CSV exception-path fallback, same values).
        val FALLBACK =
            TasteProfile(
                energy = 69.0,
                valence = 67.0,
                dance = 60.0,
                bpm = 120.0,
                acousticness = 10.0,
                instrumentalness = 5.0,
                loudness = -8.0,
            )
    }
}
