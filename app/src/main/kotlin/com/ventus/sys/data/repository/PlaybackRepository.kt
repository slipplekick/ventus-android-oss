package com.ventus.sys.data.repository

import com.ventus.sys.data.auth.SpotifyTokenProvider
import com.ventus.sys.data.remote.PlaybackApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [PlaybackApi] with fresh-token injection ([SpotifyTokenProvider]).
 * Split out from [SpotifyRepository] — see that class's doc comment — but
 * follows the exact same shape: each method returns success/failure only
 * (matching app.py's own fire-and-forget `{"success": ...}`, app.py:2090-
 * 2116). A 403/404 here almost always means "no active Spotify device"
 * (phone's screen off, Spotify not open anywhere), which the caller (the
 * transport bar) surfaces as a reverted optimistic UI update, not a crash.
 * runCatching also covers a network failure the same way app.py's own bare
 * `except` does.
 */
@Singleton
class PlaybackRepository
    @Inject
    constructor(
        private val api: PlaybackApi,
        private val tokenProvider: SpotifyTokenProvider,
    ) {
        suspend fun play(): Boolean = runCatching { api.play(tokenProvider.freshAuthHeader()).isSuccessful }.getOrDefault(false)

        suspend fun pause(): Boolean = runCatching { api.pause(tokenProvider.freshAuthHeader()).isSuccessful }.getOrDefault(false)

        suspend fun skipNext(): Boolean = runCatching { api.skipNext(tokenProvider.freshAuthHeader()).isSuccessful }.getOrDefault(false)

        suspend fun skipPrevious(): Boolean =
            runCatching {
                api.skipPrevious(tokenProvider.freshAuthHeader()).isSuccessful
            }.getOrDefault(false)

        suspend fun seek(positionMs: Long): Boolean =
            runCatching { api.seek(tokenProvider.freshAuthHeader(), positionMs).isSuccessful }.getOrDefault(false)
    }
