package com.ventus.sys.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileTrackDao {
    @Query("SELECT * FROM profile_tracks")
    fun observeAll(): Flow<List<ProfileTrackEntity>>

    @Query("SELECT * FROM profile_tracks")
    suspend fun getAll(): List<ProfileTrackEntity>

    @Query("SELECT id FROM profile_tracks")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM profile_tracks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ProfileTrackEntity>)

    @Query("DELETE FROM profile_tracks WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("DELETE FROM profile_tracks")
    suspend fun clear()
}
