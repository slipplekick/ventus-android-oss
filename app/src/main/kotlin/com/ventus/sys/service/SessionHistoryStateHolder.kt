package com.ventus.sys.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRIES = 50 // app.js:1164 — historyLog.pop() once it exceeds 50.

/**
 * In-memory only, same as app.js's own `historyLog` (a plain JS array, never
 * persisted server-side except on explicit export) - resets on app restart,
 * not a Room table. Singleton so [com.ventus.sys.service.NowPlayingService]
 * (the writer) and the Session History screen (the reader) don't need a
 * reference to each other, same pattern as [NowPlayingStateHolder].
 */
@Singleton
class SessionHistoryStateHolder
    @Inject
    constructor() {
        private val _entries = MutableStateFlow<List<SessionHistoryEntry>>(emptyList())
        val entries: StateFlow<List<SessionHistoryEntry>> = _entries.asStateFlow()

        /** Newest first, matches app.js's historyLog.unshift (app.js:1155). */
        fun push(entry: SessionHistoryEntry) {
            _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        }

        fun clear() {
            _entries.value = emptyList()
        }
    }
