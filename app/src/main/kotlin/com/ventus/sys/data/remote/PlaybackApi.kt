package com.ventus.sys.data.remote

import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Transport controls — ports app.py's playback_control() (app.py:2090-2116).
 * Kept separate from [SpotifyApi] (not merged in) specifically so this
 * concern doesn't push that interface over detekt's TooManyFunctions
 * threshold, which the read endpoints (search, top tracks, queue) already
 * compete for room under.
 *
 * All return 204/200 with no body on success; a bare Response<Unit> is
 * enough since callers only need isSuccessful ([com.ventus.sys.data.repository.PlaybackRepository],
 * matching app.py's own `{"success": res.status_code in [204,200]}`
 * fire-and-forget shape). No request body on play/pause/next/prev -
 * Spotify's play endpoint supports one (context_uri/uris) for starting
 * specific content, but resuming current playback needs none, same as
 * desktop.
 */
interface PlaybackApi {
    @PUT("me/player/play")
    suspend fun play(
        @Header("Authorization") auth: String,
    ): Response<Unit>

    @PUT("me/player/pause")
    suspend fun pause(
        @Header("Authorization") auth: String,
    ): Response<Unit>

    @POST("me/player/next")
    suspend fun skipNext(
        @Header("Authorization") auth: String,
    ): Response<Unit>

    @POST("me/player/previous")
    suspend fun skipPrevious(
        @Header("Authorization") auth: String,
    ): Response<Unit>

    @PUT("me/player/seek")
    suspend fun seek(
        @Header("Authorization") auth: String,
        @Query("position_ms") positionMs: Long,
    ): Response<Unit>
}
