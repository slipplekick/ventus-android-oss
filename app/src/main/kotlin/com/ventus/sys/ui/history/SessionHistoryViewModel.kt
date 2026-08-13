package com.ventus.sys.ui.history

import androidx.lifecycle.ViewModel
import com.ventus.sys.service.SessionHistoryEntry
import com.ventus.sys.service.SessionHistoryStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val CSV_TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private val CSV_HEADER = listOf("time", "name", "artist", "score", "verdict", "method", "id")

/** Ports app.js's Session History page (renderHistory/exportSessionLog, app.js:625-665, 1676-1696). */
@HiltViewModel
class SessionHistoryViewModel
    @Inject
    constructor(
        private val stateHolder: SessionHistoryStateHolder,
    ) : ViewModel() {
        val entries: StateFlow<List<SessionHistoryEntry>> = stateHolder.entries

        fun clear() = stateHolder.clear()

        /**
         * Ports app.py's export_session (app.py:2061-2085) CSV shape exactly
         * (same column order/names) but built entirely on-device - there's no
         * backend to POST the log to and receive a CSV back from, so this
         * writes the file directly. The caller (SessionHistoryScreen) hands
         * the resulting string to a SAF CreateDocument result's OutputStream.
         */
        fun buildCsv(): String {
            val rows = entries.value.map { it.toCsvRow() }
            return (listOf(CSV_HEADER) + rows).joinToString("\n") { row -> row.joinToString(",") { csvEscape(it) } }
        }
    }

private fun SessionHistoryEntry.toCsvRow(): List<String> =
    listOf(
        CSV_TIMESTAMP_FORMAT.format(Date(timestampMs)),
        name,
        artist,
        score?.toString() ?: "",
        verdict?.display ?: "",
        method,
        id,
    )

/** Quotes a field only when it needs it (contains a comma, quote, or newline) — matches standard CSV escaping. */
private fun csvEscape(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\n' }) {
        "\"${field.replace("\"", "\"\"")}\""
    } else {
        field
    }
