package com.ventus.sys.data.remote

import com.ventus.sys.data.remote.dto.RecentlyPlayedResponse
import com.ventus.sys.data.remote.dto.SpotifyTopArtistsResponse
import com.ventus.sys.data.remote.dto.SpotifyTopTracksResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Top tracks/artists + recently-played — the Signals screen's source, split
 * out from [SpotifyApi] from the start (not added there and split later)
 * to avoid re-hitting detekt's TooManyFunctions the way [PlaybackApi] was
 * split out to avoid the same threshold earlier.
 */
interface SpotifyStatsApi {
    /** Ports app.py's get_top (app.py:2234-2274), type=tracks — the Signals screen's top-tracks tab. */
    @GET("me/top/tracks")
    suspend fun getTopTracks(
        @Header("Authorization") auth: String,
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int = 50,
    ): SpotifyTopTracksResponse

    /** Ports app.py's get_top (app.py:2234-2274), type=artists — the Signals screen's top-artists tab. */
    @GET("me/top/artists")
    suspend fun getTopArtists(
        @Header("Authorization") auth: String,
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int = 50,
    ): SpotifyTopArtistsResponse

    /** Ports app.py's recently_played (app.py:2169-2231) — the Signals screen's recently-played tab. */
    @GET("me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
    ): RecentlyPlayedResponse
}
