package com.ventus.sys.data.repository

import retrofit2.HttpException

private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403

/**
 * Turns a Retrofit HttpException from a playlist-fetch call into a message
 * worth showing the user — shared between TasteProfileRepository's onboarding
 * sync and the Playlist Audit screen, both of which take a user-typed
 * playlist ID/URL where a 404 (wrong ID, deleted playlist) or 403 (private,
 * not yours) is a genuinely common real input, not an edge case.
 */
fun playlistFetchErrorMessage(e: HttpException): String =
    when (e.code()) {
        HTTP_NOT_FOUND -> "Playlist not found — check the ID or URL"
        HTTP_FORBIDDEN -> "Can't access this playlist — it may be private"
        else -> "Spotify fetch failed (${e.code()})"
    }
