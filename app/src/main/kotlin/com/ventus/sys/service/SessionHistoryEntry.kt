package com.ventus.sys.service

import com.ventus.sys.domain.model.Verdict

/**
 * One row of the in-session activity log — mirrors app.js's historyLog
 * entries (app.js:1155-1163). `score`/`verdict` are null for a track that
 * never resolved (matches the log's own "UNRATED"/ERR handling,
 * renderHistory app.js:642-660), not a crash or a dropped row. `method`
 * mirrors [com.ventus.sys.domain.ResolutionMethod] (vault cache vs. a
 * fresh ReccoBeats fetch vs. unresolved) - desktop's richer 5-tier
 * "method" label doesn't apply here (Ghost Signal etc. are desktop-only).
 */
data class SessionHistoryEntry(
    val id: String,
    val name: String,
    val artist: String,
    val score: Int?,
    val verdict: Verdict?,
    val method: String,
    val timestampMs: Long,
)
