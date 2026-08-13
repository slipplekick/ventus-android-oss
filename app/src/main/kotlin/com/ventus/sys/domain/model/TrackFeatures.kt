package com.ventus.sys.domain.model

/**
 * A track's resolved audio features — mirrors the `vault` table schema in
 * app.py's `_init_db()` (app.py:153-169). All values are already in the
 * app's native 0-100 (or native BPM/dB) scale by the time they reach this
 * model; source-specific rescaling (e.g. ReccoBeats' 0.0-1.0 floats) is a
 * data-layer concern, not a domain one.
 */
data class TrackFeatures(
    val id: String,
    val energy: Double,
    val valence: Double,
    val danceability: Double,
    val bpm: Double,
    val acousticness: Double,
    val instrumentalness: Double,
    val loudness: Double,
    val key: Int = -1,
    val mode: Int = 1,
)
