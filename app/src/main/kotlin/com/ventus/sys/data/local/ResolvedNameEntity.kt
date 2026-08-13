package com.ventus.sys.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lazily-resolved song/artist names for vault-only tracks. Only needed for
 * tracks that were scored (Live Scorer, Discover, Queue) but were never part
 * of any synced taste-profile playlist, since those already carry their
 * names via ProfileTrackEntity.
 *
 * Persisted (not just an in-memory cache) so a track already resolved once
 * doesn't get re-fetched from Spotify every time the Vault screen
 * recomposes: once enough tracks get Live-Scorer-only played (not synced via
 * a playlist), a meaningful fraction of the Vault list would otherwise show
 * raw truncated Spotify IDs instead of real names — this happens fast enough
 * from normal Live Scorer use that it's worth resolving eagerly and caching.
 */
@Entity(tableName = "resolved_names")
data class ResolvedNameEntity(
    @PrimaryKey val id: String,
    val song: String,
    val artist: String,
)
