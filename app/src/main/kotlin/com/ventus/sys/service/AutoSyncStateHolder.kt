package com.ventus.sys.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AutoSyncStatus(
    val lastRunAtMs: Long? = null,
    val lastMessage: String? = null,
)

/**
 * Surfaces [NowPlayingService]'s periodic auto-sync results (see its own
 * doc comment on the auto-sync loop) to the Settings screen — same
 * writer/reader-decoupling purpose as [NowPlayingStateHolder].
 */
@Singleton
class AutoSyncStateHolder
    @Inject
    constructor() {
        private val _status = MutableStateFlow(AutoSyncStatus())
        val status: StateFlow<AutoSyncStatus> = _status.asStateFlow()

        fun update(
            atMs: Long,
            message: String,
        ) {
            _status.value = AutoSyncStatus(lastRunAtMs = atMs, lastMessage = message)
        }
    }
