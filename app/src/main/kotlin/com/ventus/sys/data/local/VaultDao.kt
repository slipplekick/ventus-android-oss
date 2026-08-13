package com.ventus.sys.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault WHERE id = :id")
    suspend fun get(id: String): VaultEntity?

    @Query("SELECT * FROM vault WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<VaultEntity>

    @Query("SELECT * FROM vault")
    fun observeAll(): Flow<List<VaultEntity>>

    @Query("SELECT COUNT(*) FROM vault")
    suspend fun count(): Int

    // Called only from VaultRepository.upsert(), never directly — that's
    // where the protected-source filtering (app.py's vault_insert rule)
    // happens before rows ever reach here. REPLACE is safe at this layer
    // because the caller has already excluded anything it shouldn't overwrite.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<VaultEntity>)

    @Query("SELECT id FROM vault WHERE id IN (:ids) AND source IN ('local_engine', 'spotify_af', 'audio_analysis')")
    suspend fun getProtectedIds(ids: List<String>): List<String>

    @Query("DELETE FROM vault WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)
}
