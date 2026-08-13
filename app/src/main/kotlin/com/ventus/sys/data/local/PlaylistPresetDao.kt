package com.ventus.sys.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistPresetDao {
    @Query("SELECT * FROM playlist_presets ORDER BY name ASC")
    fun observeAll(): Flow<List<PlaylistPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaylistPresetEntity)

    @Delete
    suspend fun delete(entity: PlaylistPresetEntity)
}
