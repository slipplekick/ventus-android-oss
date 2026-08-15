package com.ventus.sys.data.remote

import com.ventus.sys.data.remote.dto.AddPlaylistItemsRequest
import com.ventus.sys.data.remote.dto.AddPlaylistItemsResponse
import com.ventus.sys.data.remote.dto.CurrentlyPlayingResponse
import com.ventus.sys.data.remote.dto.PlaylistItemsPageResponse
import com.ventus.sys.data.remote.dto.PlaylistMetaResponse
import com.ventus.sys.data.remote.dto.SpotifyQueueResponse
import com.ventus.sys.data.remote.dto.UserPlaylistsPageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

private const val DEFAULT_PLAYLIST_FIELDS =
    "items(item(id,name,artists,album(images)),track(id,name,artists,album(images))),next"

/**
 * Core subset of Spotify's Web API — only the endpoints onboarding sync,
 * the live poll, and track-name resolution actually need. Other screens
 * (search, add-to-playlist, queue) add their own methods here following
 * this same pattern rather than defining everything speculatively upfront.
 * Playback transport lives in [PlaybackApi] instead, top tracks/artists/
 * recently-played live in [SpotifyStatsApi] (the Signals screen's
 * endpoints), and single-track lookup/search live in [SpotifySearchApi]
 * (split out once playlist pagination pushed this interface to 11
 * functions) — all split by concern rather than adding here and hitting
 * detekt's TooManyFunctions threshold on a single bloated interface.
 *
 * Auth: token passed explicitly per-call via @Header, not a blanket OkHttp
 * interceptor — SpotifyRepository fetches a fresh token (PkceAuthManager,
 * refreshing via AppAuth if expired) before each call, which needs a
 * suspend function; an interceptor would need runBlocking to do the same,
 * which is worse.
 */
interface SpotifyApi {
    @GET("me/player/currently-playing")
    suspend fun getCurrentlyPlaying(
        @Header("Authorization") auth: String,
    ): Response<CurrentlyPlayingResponse>

    @GET("playlists/{playlistId}/items")
    suspend fun getPlaylistItems(
        @Header("Authorization") auth: String,
        @Path("playlistId") playlistId: String,
        @Query("fields") fields: String = DEFAULT_PLAYLIST_FIELDS,
        @Query("limit") limit: Int = 100,
    ): PlaylistItemsPageResponse

    @GET("me/tracks")
    suspend fun getLikedSongs(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
    ): PlaylistItemsPageResponse

    /** Follows a `next` pagination URL directly — Spotify returns a full URL, not just an offset. */
    @GET
    suspend fun getPage(
        @Header("Authorization") auth: String,
        @Url url: String,
    ): PlaylistItemsPageResponse

    @GET("playlists/{playlistId}")
    suspend fun getPlaylistMeta(
        @Header("Authorization") auth: String,
        @Path("playlistId") playlistId: String,
        @Query("fields") fields: String = "name",
    ): PlaylistMetaResponse

    @GET("me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
    ): UserPlaylistsPageResponse

    /** Follows a `next` pagination URL for the user's playlists list — same pattern as [getPage] for playlist items. */
    @GET
    suspend fun getUserPlaylistsPage(
        @Header("Authorization") auth: String,
        @Url url: String,
    ): UserPlaylistsPageResponse

    /** Ports app.py's get_queue (app.py:1622-1663) — the Queue Analyzer screen's source. */
    @GET("me/player/queue")
    suspend fun getQueue(
        @Header("Authorization") auth: String,
    ): Response<SpotifyQueueResponse>

    /** Ports app.py's add_to_playlist's final POST (app.py:1752-1754) — Auto-Add's write step. */
    @POST("playlists/{playlistId}/items")
    suspend fun addPlaylistItems(
        @Header("Authorization") auth: String,
        @Path("playlistId") playlistId: String,
        @Body body: AddPlaylistItemsRequest,
    ): Response<AddPlaylistItemsResponse>
}
