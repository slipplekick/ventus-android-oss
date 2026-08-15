package com.ventus.sys.data.remote

import com.ventus.sys.data.remote.dto.SpotifySearchResponse
import com.ventus.sys.data.remote.dto.SpotifyTrackDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Single-track lookup + search — split out from [SpotifyApi] once adding
 * user-playlist pagination pushed that interface to 11 functions, same
 * TooManyFunctions reason [PlaybackApi]/[SpotifyStatsApi] were split out for
 * earlier. Both endpoints here are "look up a track by identity" (by ID, or
 * by query), distinct from [SpotifyApi]'s playlist/queue/currently-playing
 * concerns.
 */
interface SpotifySearchApi {
    @GET("tracks/{id}")
    suspend fun getTrack(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): Response<SpotifyTrackDto>

    /** Ports app.py's search_spotify (app.py:1600-1618) — track search for the Discover screen. */
    @GET("search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 8,
    ): SpotifySearchResponse
}
