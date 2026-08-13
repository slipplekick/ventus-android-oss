package com.ventus.sys.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResolvedNameDao {
    @Query("SELECT * FROM resolved_names")
    fun observeAll(): Flow<List<ResolvedNameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolvedNameEntity)
}
