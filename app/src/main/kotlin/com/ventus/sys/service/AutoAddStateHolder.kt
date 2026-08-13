package com.ventus.sys.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AutoAddStatus(
    val lastRunAtMs: Long? = null,
    val lastMessage: String? = null,
    val lastWasError: Boolean = false,
)

/**
 * Surfaces [NowPlayingService]'s Auto-Add trigger results to the Settings
 * screen — same writer/reader-decoupling purpose as [AutoSyncStateHolder],
 * standing in for app.js's showToast() calls from maybeAutoAdd (app.js:1664/
 * 1666/1669), which have no mobile-background equivalent since the service
 * runs headless.
 */
@Singleton
class AutoAddStateHolder
    @Inject
    constructor() {
        private val _status = MutableStateFlow(AutoAddStatus())
        val status: StateFlow<AutoAddStatus> = _status.asStateFlow()

        fun update(
            atMs: Long,
            message: String,
            isError: Boolean,
        ) {
            _status.value = AutoAddStatus(lastRunAtMs = atMs, lastMessage = message, lastWasError = isError)
        }
    }
