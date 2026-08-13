package com.ventus.sys.data.repository

import com.ventus.sys.data.auth.SpotifyTokenProvider
import com.ventus.sys.data.remote.SpotifyApi
import com.ventus.sys.data.remote.dto.AddPlaylistItemsRequest
import com.ventus.sys.data.remote.dto.PlaylistItemsPageResponse
import com.ventus.sys.data.remote.dto.SpotifyTrackDto
import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_RETRIES = 3
private const val MAX_WAIT_SECONDS = 30L // app.py:1685's real-world-caught ceiling — see class doc.
private const val DEFAULT_BACKOFF_SECONDS = 2L

// Same field set as app.py's add_to_playlist dup scan (app.py:1732) —
// needs external_ids (ISRC) on top of the id/name/artists this port's other
// playlist-item fetches already request.
private const val DUP_SCAN_FIELDS = "items(item(id,name,artists,external_ids),track(id,name,artists,external_ids)),next"

sealed interface AutoAddResult {
    data class Added(
        val trackName: String,
    ) : AutoAddResult

    data class AlreadyInPlaylist(
        val trackName: String,
    ) : AutoAddResult

    data class Failed(
        val message: String,
    ) : AutoAddResult
}

/**
 * Ports app.py's add_to_playlist (app.py:1712-1759) and its retry helper
 * _spotify_retry_429 (app.py:1673-1710) — Auto-Add's write path, triggered
 * from [com.ventus.sys.service.NowPlayingService] right after a track scores
 * above the configured threshold, same call site app.js's maybeAutoAdd fires
 * from (right after triggerScore/silentVaultScore).
 *
 * The 429 retry specifically bounds its wait to [MAX_WAIT_SECONDS] rather
 * than trusting Spotify's Retry-After header unbounded — app.py's own doc
 * comment on _spotify_retry_429 records a real live incident where Spotify
 * sent back a ~23-hour Retry-After under heavy request volume, which the
 * unbounded version dutifully slept through inside a request handler.
 * Ported here as the same fixed ceiling from the start, not re-discovered.
 */
@Singleton
class AutoAddRepository
    @Inject
    constructor(
        private val api: SpotifyApi,
        private val spotifyRepository: SpotifyRepository,
        private val tokenProvider: SpotifyTokenProvider,
    ) {
        // Mirrors app.py's SESSION_ADDED_TRACKS (module-level set, app.py:1719)
        // — in-memory only, cleared on process restart same as desktop's.
        private val sessionAdded = mutableSetOf<String>()

        suspend fun addToPlaylist(
            playlistId: String,
            trackId: String,
            trackName: String,
        ): AutoAddResult {
            val cacheKey = "$playlistId:$trackId"
            if (cacheKey in sessionAdded) return AutoAddResult.AlreadyInPlaylist(trackName)

            val track = spotifyRepository.getTrack(trackId) ?: return AutoAddResult.Failed("Could not verify track")
            val auth = tokenProvider.freshAuthHeader()

            val scanResult =
                runCatching { isDuplicate(auth, playlistId, trackId, track) }
                    .getOrElse { e -> return AutoAddResult.Failed(scanErrorMessage(e)) }

            if (scanResult) {
                sessionAdded += cacheKey
                return AutoAddResult.AlreadyInPlaylist(trackName)
            }

            return postToPlaylist(auth, playlistId, trackId, trackName, cacheKey)
        }

        private suspend fun isDuplicate(
            auth: String,
            playlistId: String,
            trackId: String,
            track: SpotifyTrackDto,
        ): Boolean {
            val targetIsrc = track.externalIds?.isrc
            val targetName = normalizeTrackName(track.name)
            val targetArtist = track.artistName.lowercase().trim()

            var page = retryThrowingOn429 { api.getPlaylistItems(auth, playlistId, fields = DUP_SCAN_FIELDS) }
            while (true) {
                if (pageContainsMatch(page, trackId, targetIsrc, targetName, targetArtist)) return true
                val next = page.next ?: return false
                page = retryThrowingOn429 { api.getPage(auth, next) }
            }
        }

        private fun pageContainsMatch(
            page: PlaylistItemsPageResponse,
            trackId: String,
            targetIsrc: String?,
            targetName: String,
            targetArtist: String,
        ): Boolean =
            page.items.any { entry ->
                val t = entry.resolvedTrack ?: return@any false
                val tid = t.id ?: return@any false
                val isrc = t.externalIds?.isrc
                tid == trackId ||
                    (targetIsrc != null && isrc != null && targetIsrc == isrc) ||
                    (normalizeTrackName(t.name) == targetName && t.artistName.lowercase().trim() == targetArtist)
            }

        private suspend fun postToPlaylist(
            auth: String,
            playlistId: String,
            trackId: String,
            trackName: String,
            cacheKey: String,
        ): AutoAddResult {
            val response =
                try {
                    retryResponseOn429 {
                        api.addPlaylistItems(
                            auth,
                            playlistId,
                            AddPlaylistItemsRequest(uris = listOf("spotify:track:$trackId")),
                        )
                    }
                } catch (e: IOException) {
                    return AutoAddResult.Failed(e.message ?: "Failed to add track")
                }
            return if (response.isSuccessful) {
                sessionAdded += cacheKey
                AutoAddResult.Added(trackName)
            } else {
                AutoAddResult.Failed("Spotify returned ${response.code()}")
            }
        }

        // Ports app.py's `t.get('name','').split(' - ')[0].split(' (')[0].lower().strip()`
        // (app.py:1729/1745) — strips " - Remix"/" (feat. ...)"-style suffixes
        // so e.g. "Song - Radio Edit" still matches a plain "Song" already in
        // the playlist.
        private fun normalizeTrackName(name: String): String =
            name
                .substringBefore(" - ")
                .substringBefore(" (")
                .lowercase()
                .trim()

        private fun scanErrorMessage(e: Throwable): String =
            when (e) {
                is HttpException -> "Failed to scan playlist (${e.code()})"
                is IOException -> e.message ?: "Failed to scan playlist"
                else -> throw e
            }

        private suspend fun <T> retryThrowingOn429(block: suspend () -> T): T {
            var attempt = 0
            while (true) {
                try {
                    return block()
                } catch (e: HttpException) {
                    if (e.code() != HTTP_TOO_MANY_REQUESTS || attempt >= MAX_RETRIES - 1) throw e
                    delay(retryAfterMs(e.response()?.headers()?.get("Retry-After"), attempt))
                    attempt++
                }
            }
        }

        private suspend fun <T> retryResponseOn429(block: suspend () -> Response<T>): Response<T> {
            var attempt = 0
            var response = block()
            while (response.code() == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES - 1) {
                delay(retryAfterMs(response.headers()["Retry-After"], attempt))
                attempt++
                response = block()
            }
            return response
        }

        private fun retryAfterMs(
            headerValue: String?,
            attempt: Int,
        ): Long {
            val seconds = headerValue?.toLongOrNull() ?: (DEFAULT_BACKOFF_SECONDS * (attempt + 1))
            return seconds.coerceIn(0, MAX_WAIT_SECONDS) * MILLIS_PER_SECOND
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1000L
        }
    }
