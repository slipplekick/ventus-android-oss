package com.ventus.sys.data.repository

import com.ventus.sys.data.auth.SpotifyTokenProvider
import com.ventus.sys.data.remote.SpotifyApi
import com.ventus.sys.data.remote.SpotifySearchApi
import com.ventus.sys.data.remote.SpotifyStatsApi
import com.ventus.sys.data.remote.dto.PlaylistItemEntryDto
import com.ventus.sys.data.remote.dto.SpotifyTopArtistDto
import com.ventus.sys.data.remote.dto.SpotifyTrackDto
import com.ventus.sys.data.remote.dto.UserPlaylistDto
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_RETRIES = 3
private const val BASE_BACKOFF_MS = 1000L

/** A deduplicated recently-played entry — mirrors app.py's own seen-by-id dedup (app.py:2189-2198). */
data class RecentlyPlayedTrack(
    val id: String,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val playedAt: String,
)

/** A resolved now-playing snapshot — the subset app.py's now_playing poller broadcasts. */
data class NowPlaying(
    val trackId: String,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
)

/**
 * Wraps [SpotifyApi] with fresh-token injection ([SpotifyTokenProvider]) and
 * pagination. Sentinel playlist ID for Liked Songs mirrors app.py's
 * LIKED_SONGS_SENTINEL (app.py:968) so callers (onboarding's sync,
 * TasteProfileRepository's sync) don't need their own special-casing — same
 * pattern, same reason.
 *
 * Playback transport (play/pause/skip/seek) lives in [PlaybackRepository]
 * instead of here — detekt's TooManyFunctions flagged this class approaching
 * its threshold once search, top tracks, recently-played, and queue all
 * needed a home too, so splitting by concern (read vs. transport) is the
 * sustainable fix, not a one-off threshold bump that just gets hit again.
 */
@Singleton
class SpotifyRepository
    @Inject
    constructor(
        private val api: SpotifyApi,
        private val statsApi: SpotifyStatsApi,
        private val searchApi: SpotifySearchApi,
        private val tokenProvider: SpotifyTokenProvider,
    ) {
        companion object {
            const val LIKED_SONGS_SENTINEL = "__liked_songs__"
        }

        /**
         * Returns null only when nothing is loaded at all (no active device, or
         * Spotify hasn't been opened this session) - a *paused* track still
         * comes back with real data (isPlaying=false), it just isn't actively
         * advancing. Distinguishing these two matters now that playback can be
         * paused from inside VENTUS itself (the transport bar): treating a
         * pause as "nothing playing" would wipe the whole Live Scorer/transport
         * bar back to idle a poll cycle after every pause tap, which is exactly
         * wrong for a control the user just pressed on purpose.
         */
        suspend fun getCurrentlyPlaying(): NowPlaying? {
            val response = api.getCurrentlyPlaying(tokenProvider.freshAuthHeader())
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val item = body.item ?: return null
            if (item.id == null) return null
            return NowPlaying(
                trackId = item.id,
                name = item.name,
                artist = item.artistName,
                albumArtUrl = item.albumArtUrl,
                progressMs = body.progressMs,
                durationMs = item.durationMs,
                isPlaying = body.isPlaying,
            )
        }

        /** Fetches every track in a playlist, following pagination. Liked Songs uses the sentinel ID. */
        suspend fun getPlaylistTracks(playlistId: String): List<SpotifyTrackDto> {
            val auth = tokenProvider.freshAuthHeader()
            val allEntries = mutableListOf<PlaylistItemEntryDto>()

            var page =
                if (playlistId == LIKED_SONGS_SENTINEL) {
                    api.getLikedSongs(auth)
                } else {
                    api.getPlaylistItems(auth, playlistId)
                }
            allEntries += page.items
            while (page.next != null) {
                page = api.getPage(auth, page.next!!)
                allEntries += page.items
            }
            return allEntries.mapNotNull { it.resolvedTrack }.filter { it.id != null }
        }

        suspend fun getPlaylistName(playlistId: String): String =
            runCatching { api.getPlaylistMeta(tokenProvider.freshAuthHeader(), playlistId).name }.getOrDefault(playlistId)

        /**
         * Fully paginated - stopping at the first 50 (Spotify's max page
         * size) was a real gap, not a theoretical one: Master Vault's
         * "Import All" silently missed every playlist past #50 for an
         * account with more than that. Follows `next` the same way
         * [getPlaylistTracks] already does for playlist items.
         */
        suspend fun getUserPlaylists(): List<UserPlaylistDto> {
            val auth = tokenProvider.freshAuthHeader()
            val allItems = mutableListOf<UserPlaylistDto>()

            var page = api.getUserPlaylists(auth)
            allItems += page.items.filterNotNull()
            while (page.next != null) {
                page = api.getUserPlaylistsPage(auth, page.next!!)
                allItems += page.items.filterNotNull()
            }
            return allItems
        }

        /**
         * Retries on 429 with backoff (mirrors ReccoBeatsRepository's own
         * requestWithRetry) — added after a real 429 surfaced from
         * VaultRepository's lazy name-resolution calling this for every
         * vault-only track in a burst. A single silent failure here just
         * means one track stays labeled "VAULT ONLY" instead of getting its
         * real name back on retry.
         */
        suspend fun getTrack(trackId: String): SpotifyTrackDto? {
            val auth = tokenProvider.freshAuthHeader()
            var attempt = 0
            while (attempt < MAX_RETRIES) {
                val response = searchApi.getTrack(auth, trackId)
                if (response.code() != HTTP_TOO_MANY_REQUESTS) {
                    return if (response.isSuccessful) response.body() else null
                }
                attempt++
                if (attempt < MAX_RETRIES) delay(BASE_BACKOFF_MS * (1L shl attempt)) // 2s, 4s
            }
            return null
        }

        /** Ports app.py's search_spotify (app.py:1600-1618) — track search for the Discover screen. */
        suspend fun search(query: String): List<SpotifyTrackDto> = searchApi.search(tokenProvider.freshAuthHeader(), query).tracks.items

        /** Null means no active player (app.py's own 404 "No active player" case, app.py:1628) — not an error to surface loudly. */
        suspend fun getQueue(): List<SpotifyTrackDto>? {
            val response = api.getQueue(tokenProvider.freshAuthHeader())
            if (!response.isSuccessful) return null
            return response.body()?.queue
        }

        /** Ports app.py's get_top (app.py:2234-2274), type=tracks — the Signals screen's top-tracks tab. */
        suspend fun getTopTracks(
            timeRange: String,
            limit: Int = 50,
        ): List<SpotifyTrackDto> = statsApi.getTopTracks(tokenProvider.freshAuthHeader(), timeRange, limit).items

        /** Ports app.py's get_top (app.py:2234-2274), type=artists — the Signals screen's top-artists tab. */
        suspend fun getTopArtists(
            timeRange: String,
            limit: Int = 50,
        ): List<SpotifyTopArtistDto> = statsApi.getTopArtists(tokenProvider.freshAuthHeader(), timeRange, limit).items

        /**
         * Ports app.py's recently_played (app.py:2169-2231), including its
         * dedup rule: the same track can appear multiple times (replayed) -
         * keep only the most recent play per track ID, then sort by that
         * timestamp descending. ISO-8601 UTC strings (Spotify's played_at
         * format) sort correctly with plain string comparison, same as
         * app.py's own `it['played_at'] > seen[tid]['played_at']` check.
         */
        suspend fun getRecentlyPlayed(limit: Int = 50): List<RecentlyPlayedTrack> {
            val raw = statsApi.getRecentlyPlayed(tokenProvider.freshAuthHeader(), limit).items
            val candidates =
                raw.mapNotNull { entry ->
                    val track = entry.track ?: return@mapNotNull null
                    val id = track.id ?: return@mapNotNull null
                    id to
                        RecentlyPlayedTrack(
                            id = id,
                            name = track.name,
                            artist = track.artistName,
                            albumArtUrl = track.albumArtUrl,
                            playedAt = entry.playedAt,
                        )
                }
            val seen = LinkedHashMap<String, RecentlyPlayedTrack>()
            for ((id, candidate) in candidates) {
                val existing = seen[id]
                if (existing == null || candidate.playedAt > existing.playedAt) {
                    seen[id] = candidate
                }
            }
            return seen.values.sortedByDescending { it.playedAt }
        }
    }
