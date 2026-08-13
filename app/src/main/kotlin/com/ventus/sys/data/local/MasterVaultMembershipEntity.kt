package com.ventus.sys.data.local

import androidx.room.Entity

/**
 * Which imported playlist(s) a track belongs to — ports app.py's
 * master_vault_membership table (app.py:231-239) exactly, including the
 * composite (playlistId, trackId) key (a track can legitimately sit in more
 * than one imported playlist).
 *
 * Deliberately does NOT mirror app.py's separate `master_vault` feature
 * table: Android already has one shared audio-feature cache ([VaultEntity],
 * via [com.ventus.sys.data.repository.VaultRepository]) that every screen
 * resolves through (Vault, Discover, Queue, Signals, Audit) — duplicating
 * that cache into a second table just to match desktop's literal schema
 * would mean the same track's features could drift between the two caches.
 * song/artist are denormalized onto this row instead (captured once at
 * import time from the Spotify API response already fetched for the sync),
 * so browsing Master Vault needs no separate name-resolution pass.
 */
@Entity(tableName = "master_vault_membership", primaryKeys = ["playlistId", "trackId"])
data class MasterVaultMembershipEntity(
    val playlistId: String,
    val playlistName: String,
    val trackId: String,
    val song: String,
    val artist: String,
    val addedAtMs: Long,
)
