package com.ventus.sys.domain

/**
 * Extracts a bare Spotify ID from a full URL, a `spotify:playlist:` URI, or
 * an already-bare ID — mirrors app.js's firstRunConfirmManual parsing
 * (app.js:2436-2447). Shared so every "paste a playlist ID or URL" input
 * (onboarding, Playlist Audit, and any other screen with the same field)
 * parses identically instead of each screen re-deriving it.
 */
fun extractSpotifyId(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val id = trimmed.substringAfterLast('/').substringBefore('?').removePrefix("spotify:playlist:")
    return id.ifEmpty { null }
}
