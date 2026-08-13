package com.ventus.sys.data.repository

import com.ventus.sys.data.local.MasterVaultMembershipDao
import com.ventus.sys.data.local.MasterVaultMembershipEntity
import com.ventus.sys.data.local.MasterVaultPlaylistSummary
import com.ventus.sys.domain.Camelot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class MasterVaultTrackUiItem(
    val id: String,
    val song: String,
    val artist: String,
    val camelot: String,
    val energy: Int?,
    val valence: Int?,
    val bpm: Int?,
    val isGhost: Boolean,
)

data class MasterVaultStats(
    val total: Int,
    val indexed: Int,
    val ghost: Int,
)

data class MasterVaultSyncResult(
    val playlistName: String,
    val totalInPlaylist: Int,
    val added: Int,
    val ghost: Int,
    val already: Int,
)

/**
 * Ports app.py's master_vault_sync/master_vault_list/master_vault_playlists/
 * master_vault_clear/master_vault_stats (app.py:2345-2550) — import any
 * playlist to index it, browse everything ever imported (or one playlist at
 * a time), CSV-free on Android (see [com.ventus.sys.ui.mastervault.MasterVaultScreen]'s
 * doc comment for why).
 *
 * Reuses [VaultRepository]'s shared feature cache instead of maintaining a
 * second one (see [MasterVaultMembershipEntity]'s doc comment) — this
 * repository's only real state is playlist membership.
 */
@Singleton
class MasterVaultRepository
    @Inject
    constructor(
        private val dao: MasterVaultMembershipDao,
        private val spotifyRepository: SpotifyRepository,
        private val vaultRepository: VaultRepository,
    ) {
        fun observePlaylists(): Flow<List<MasterVaultPlaylistSummary>> = dao.observePlaylists()

        /** Null/blank playlistId means "every playlist ever imported", deduplicated by track (app.py:2387-2388's no-filter branch). */
        fun observeTracks(playlistId: String?): Flow<List<MasterVaultTrackUiItem>> {
            val membership = if (playlistId.isNullOrBlank()) dao.observeAll() else dao.observeByPlaylist(playlistId)
            return combine(membership, vaultRepository.observeAll()) { rows, vaultTracks ->
                val vaultById = vaultTracks.associateBy { it.id }
                val distinctRows = if (playlistId.isNullOrBlank()) rows.distinctBy { it.trackId } else rows
                distinctRows.map { m ->
                    val feature = vaultById[m.trackId]
                    MasterVaultTrackUiItem(
                        id = m.trackId,
                        song = m.song,
                        artist = m.artist,
                        camelot = feature?.let { Camelot.get(it.key, it.mode) } ?: "--",
                        energy = feature?.energy?.toInt(),
                        valence = feature?.valence?.toInt(),
                        bpm = feature?.bpm?.toInt(),
                        isGhost = feature == null,
                    )
                }
            }
        }

        fun observeStats(): Flow<MasterVaultStats> =
            combine(dao.observeAll(), vaultRepository.observeAll()) { rows, vaultTracks ->
                val distinctIds = rows.map { it.trackId }.toSet()
                val vaultIds = vaultTracks.map { it.id }.toSet()
                val indexed = distinctIds.count { it in vaultIds }
                MasterVaultStats(total = distinctIds.size, indexed = indexed, ghost = distinctIds.size - indexed)
            }

        /**
         * Ports app.py's master_vault_sync (app.py:2402-2518) minus its CSV
         * write (no local-filesystem equivalent worth writing silently on
         * Android — see the screen's own doc comment). added/ghost/already
         * mirror app.py's own three-way split: already = was resolved before
         * this sync started, added = newly resolved just now, ghost = still
         * unresolved after trying.
         */
        suspend fun syncPlaylist(playlistId: String): MasterVaultSyncResult {
            val playlistName = spotifyRepository.getPlaylistName(playlistId)
            val remoteTracks = spotifyRepository.getPlaylistTracks(playlistId)
            val distinctTracks = remoteTracks.filter { it.id != null }.distinctBy { it.id }
            val ids = distinctTracks.mapNotNull { it.id }

            val alreadyCached = vaultRepository.getMany(ids).size
            val resolved = vaultRepository.resolveMany(ids)
            val added = resolved.size - alreadyCached
            val ghost = ids.size - resolved.size

            val nowMs = System.currentTimeMillis()
            val rows =
                distinctTracks.map { track ->
                    MasterVaultMembershipEntity(
                        playlistId = playlistId,
                        playlistName = playlistName,
                        trackId = requireNotNull(track.id),
                        song = track.name,
                        artist = track.artistName,
                        addedAtMs = nowMs,
                    )
                }
            dao.insertAll(rows)

            return MasterVaultSyncResult(
                playlistName = playlistName,
                totalInPlaylist = ids.size,
                added = added,
                ghost = ghost,
                already = alreadyCached,
            )
        }

        /**
         * Null/blank playlistId clears every playlist's membership. Neither
         * case touches [VaultRepository]'s shared feature cache — app.py's own
         * per-playlist clear already documents that a track's cached features
         * should survive removing one of the playlists it's in (app.py:2524-
         * 2527); this port applies that same rule to "clear all" too, which
         * is the correct call given Android's shared-cache design (deleting
         * cached features here would silently break Vault/Discover/Queue/etc.
         * for any track that happened to overlap).
         */
        suspend fun clear(playlistId: String?) {
            if (playlistId.isNullOrBlank()) dao.deleteAll() else dao.deleteByPlaylist(playlistId)
        }
    }
