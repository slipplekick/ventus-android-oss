package com.ventus.sys.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the `vault` table schema exactly — app.py's `_init_db()`
 * (app.py:153-169). Global feature cache keyed by Spotify track ID,
 * independent of any one playlist's membership (a track scored via
 * Discover/Queue stays cached here even if it's never added to the taste
 * profile).
 *
 * `source` values match app.py's VALID_SOURCES set exactly: 'reccobeats',
 * 'local_engine', 'spotify_af', 'audio_analysis'. Android only ever WRITES
 * 'reccobeats' (the other three tiers are desktop-only — see
 * FeatureResolver's doc comment) but must still correctly READ all four so
 * an imported desktop vault export doesn't get corrupted — see
 * VaultRepository's upsert(), which ports vault_insert()'s protected-source
 * rule (app.py:275-327).
 */
@Entity(tableName = "vault")
data class VaultEntity(
    @PrimaryKey val id: String,
    val energy: Double = 0.0,
    val valence: Double = 0.0,
    val danceability: Double = 0.0,
    val bpm: Double = 0.0,
    val acousticness: Double = 0.0,
    val instrumentalness: Double = 0.0,
    val loudness: Double = 0.0,
    val key: Int = -1,
    val mode: Int = 1,
    val source: String = "reccobeats",
)
