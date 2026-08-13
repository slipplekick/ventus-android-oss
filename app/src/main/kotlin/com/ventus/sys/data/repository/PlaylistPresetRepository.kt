package com.ventus.sys.data.repository

import com.ventus.sys.data.local.PlaylistPresetDao
import com.ventus.sys.data.local.PlaylistPresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Ports app.js's loadPresets/savePresets/addPreset/deletePreset/editPreset (app.js:1513-1634). */
@Singleton
class PlaylistPresetRepository
    @Inject
    constructor(
        private val dao: PlaylistPresetDao,
    ) {
        fun observeAll(): Flow<List<PlaylistPresetEntity>> = dao.observeAll()

        suspend fun save(
            playlistId: String,
            name: String,
        ) = dao.upsert(PlaylistPresetEntity(playlistId = playlistId, name = name))

        suspend fun delete(preset: PlaylistPresetEntity) = dao.delete(preset)
    }
