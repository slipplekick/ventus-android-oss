package com.ventus.sys.domain

/**
 * Ports get_camelot()/the two lookup arrays exactly — app.py:373-380.
 * Spotify's key is a 0-11 pitch class; mode 1=major, 0=minor.
 */
object Camelot {
    private const val MIN_KEY = 0
    private const val MAX_KEY = 11
    private const val UNKNOWN = "--"

    private val MAJORS = listOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val MINORS = listOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    private fun isValidKey(key: Int) = key in MIN_KEY..MAX_KEY

    private fun isValidMode(mode: Int) = mode == 0 || mode == 1

    fun get(
        key: Int,
        mode: Int,
    ): String =
        if (isValidKey(key) && isValidMode(mode)) {
            if (mode == 1) MAJORS[key] else MINORS[key]
        } else {
            UNKNOWN
        }

    /**
     * Reverse of [get] — recovers (key, mode) from a Camelot string like
     * "5A"/"11B". Ports parse_camelot() (app.py:382-398), needed for
     * backfilling from an imported desktop CSV export where only the
     * human-readable Camelot string is stored, not the raw key/mode.
     * Returns (-1, 1) — the same "unknown" sentinel [get] itself uses — if
     * unparseable.
     */
    fun parse(camelot: String?): Pair<Int, Int> {
        val unknown = -1 to 1
        val normalized = camelot?.trim()?.uppercase()
        return when {
            normalized.isNullOrEmpty() -> unknown
            MAJORS.contains(normalized) -> MAJORS.indexOf(normalized) to 1
            MINORS.contains(normalized) -> MINORS.indexOf(normalized) to 0
            else -> unknown
        }
    }
}
