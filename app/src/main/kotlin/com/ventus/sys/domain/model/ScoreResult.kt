package com.ventus.sys.domain.model

enum class Verdict {
    CORE,
    ALIGNED,
    FRINGE,
    OUTLIER,
    NO_MATCH,

    // Empty-profile sentinel — app.py:504-505 returns this distinct string
    // rather than a numeric-threshold verdict, deliberately unreachable via
    // score_features' own >=25/45/65/85 thresholds (app.py:523-527).
    NO_SIGNAL,
    ;

    /** Matches app.py's display strings exactly (e.g. "NO MATCH", not "NO_MATCH"). */
    val display: String
        get() = name.replace('_', ' ')
}

/** Per-axis absolute deltas, rounded to 1 decimal — app.py:528-532's `deltas` dict. */
data class ScoreDeltas(
    val energy: Double,
    val valence: Double,
    val dance: Double,
    val acoustic: Double,
    val instrumental: Double,
    val loudness: Double,
    val bpm: Double,
)

data class ScoreResult(
    val score: Int,
    val verdict: Verdict,
    val deltas: ScoreDeltas,
)
