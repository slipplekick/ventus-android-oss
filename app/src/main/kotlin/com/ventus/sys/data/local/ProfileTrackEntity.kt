package com.ventus.sys.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Mirrors master_vibe_training_set.csv's row shape — the tracks that make
 * up the CURRENT taste-profile playlist (desktop's `_sync_playlist_core`,
 * app.py:1113-1280), not the global vault. Two distinct concepts on
 * desktop too: this drives [com.ventus.sys.domain.TasteProfileCalculator]'s
 * recency weighting (needs addedAt) and the Taste Profile screen's track
 * list (needs song/artist names, which the bare `vault` table doesn't
 * carry); the Track Vault screen reads the `vault` table directly and can
 * include tracks that were never part of any synced playlist (Discover
 * searches, Queue lookups, Master Vault imports).
 *
 * Removed from this table when a re-sync's diff finds it's no longer in
 * the source playlist (mirrors app.py:1164-1190's removed_ids handling,
 * including the "refuse to remove >50% of a >10-track profile in one sync"
 * guard — that guard lives in TasteProfileRepository, not here).
 */
@Entity(tableName = "profile_tracks")
data class ProfileTrackEntity(
    @PrimaryKey val id: String,
    val song: String,
    val artist: String,
    val addedAt: LocalDate,
    val energy: Double = 0.0,
    val valence: Double = 0.0,
    val danceability: Double = 0.0,
    val bpm: Double = 0.0,
    val acousticness: Double = 0.0,
    val instrumentalness: Double = 0.0,
    val loudness: Double = 0.0,
    val key: Int = -1,
    val mode: Int = 1,
)
