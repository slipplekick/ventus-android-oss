package com.ventus.sys.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MasterVaultPlaylistSummary(
    val playlistId: String,
    val playlistName: String,
    val trackCount: Int,
    val lastSyncedAtMs: Long,
)

@Dao
interface MasterVaultMembershipDao {
    @Query("SELECT * FROM master_vault_membership ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<MasterVaultMembershipEntity>>

    @Query("SELECT * FROM master_vault_membership WHERE playlistId = :playlistId ORDER BY addedAtMs DESC")
    fun observeByPlaylist(playlistId: String): Flow<List<MasterVaultMembershipEntity>>

    /** Ports app.py's master_vault_playlists (app.py:2345-2366) — the "VIEW" picker's source. */
    @Query(
        """
        SELECT playlistId, playlistName, COUNT(*) as trackCount, MAX(addedAtMs) as lastSyncedAtMs
        FROM master_vault_membership
        GROUP BY playlistId
        ORDER BY lastSyncedAtMs DESC
        """,
    )
    fun observePlaylists(): Flow<List<MasterVaultPlaylistSummary>>

    // REPLACE, not IGNORE - MasterVaultRepository.syncPlaylist rebuilds every
    // row fresh from Spotify on every sync (current playlist name, current
    // addedAtMs). IGNORE would silently drop that fresh metadata for any
    // track already imported, so a Spotify-side rename would never reach
    // the picker and a no-op re-sync (no new tracks) would never advance
    // lastSyncedAtMs, both throwing off observePlaylists's ORDER BY. Safe
    // because nothing on this row is locally-authoritative - it's entirely
    // a mirror of Spotify's own state as of the last sync.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<MasterVaultMembershipEntity>)

    @Query("DELETE FROM master_vault_membership WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM master_vault_membership")
    suspend fun deleteAll()
}
