package com.ventus.sys.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields app.py/app.js actually read are modeled — not Spotify's
// full response schema. ignoreUnknownKeys=true (configured on the shared
// Json instance in NetworkModule) means extra fields Spotify adds later
// don't break parsing.

@Serializable
data class SpotifyImageDto(
    val url: String,
)

@Serializable
data class SpotifyArtistDto(
    val name: String,
)

@Serializable
data class SpotifyAlbumDto(
    val name: String = "",
    // Spotify sends an explicit JSON `null` here (not a missing key) for some
    // albums/singles with no artwork yet - a default value only covers the
    // missing-key case, so this must be nullable or parsing throws
    // (kotlinx.serialization.json.internal.JsonDecodingException), which is
    // exactly what crashed the app on first real-device login: the very
    // first playlist fetched had `"images":null`.
    val images: List<SpotifyImageDto>? = null,
)

@Serializable
data class SpotifyExternalIdsDto(
    val isrc: String? = null,
)

@Serializable
data class SpotifyTrackDto(
    val id: String? = null,
    val name: String = "",
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto = SpotifyAlbumDto(),
    @SerialName("duration_ms") val durationMs: Long = 0,
    // Only populated when explicitly requested via a `fields` filter
    // (Auto-Add's duplicate scan, app.py:1732) - null otherwise, not absent.
    @SerialName("external_ids") val externalIds: SpotifyExternalIdsDto? = null,
) {
    val artistName: String get() = artists.firstOrNull()?.name ?: "Unknown"
    val albumArtUrl: String? get() = album.images?.firstOrNull()?.url
}

/** Request body for POST playlists/{id}/items — ports app.py's add_to_playlist (app.py:1752-1754). */
@Serializable
data class AddPlaylistItemsRequest(
    val uris: List<String>,
)

@Serializable
data class AddPlaylistItemsResponse(
    @SerialName("snapshot_id") val snapshotId: String? = null,
)

@Serializable
data class CurrentlyPlayingResponse(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val item: SpotifyTrackDto? = null,
    @SerialName("progress_ms") val progressMs: Long = 0,
)

/**
 * A playlist-items page entry. Spotify's actual field name for the nested
 * track object varies between "item" (newer API versions) and "track"
 * (older/liked-songs) — app.py's own resolution is `pl.get('item') or
 * pl.get('track')` (app.py:1160, and repeated at every other call site
 * that reads playlist items). Both are modeled here so the same
 * tolerance applies without callers needing to know which one Spotify
 * actually sent.
 */
@Serializable
data class PlaylistItemEntryDto(
    val item: SpotifyTrackDto? = null,
    val track: SpotifyTrackDto? = null,
) {
    val resolvedTrack: SpotifyTrackDto? get() = item ?: track
}

@Serializable
data class PlaylistItemsPageResponse(
    val items: List<PlaylistItemEntryDto> = emptyList(),
    val next: String? = null,
)

@Serializable
data class PlaylistMetaResponse(
    val name: String = "",
)

@Serializable
data class UserPlaylistOwnerDto(
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class UserPlaylistTrackCountDto(
    val total: Int = 0,
)

@Serializable
data class UserPlaylistDto(
    val id: String,
    val name: String = "Untitled",
    // Same explicit-JSON-null case as SpotifyAlbumDto.images above - a
    // brand-new/empty playlist sends "images":null, not a missing key.
    val images: List<SpotifyImageDto>? = null,
    val owner: UserPlaylistOwnerDto = UserPlaylistOwnerDto(),
    // Current Spotify responses use "items.total"; "tracks.total" is kept
    // as a fallback for older API versions.
    val items: UserPlaylistTrackCountDto? = null,
    val tracks: UserPlaylistTrackCountDto? = null,
) {
    val trackCount: Int get() = items?.total ?: tracks?.total ?: 0

    // Matches app.js's renderPlaylistPicker (pl.image_url, app.js:2364) - the
    // onboarding picker shows this per row with a music-note fallback.
    val coverUrl: String? get() = images?.firstOrNull()?.url
}

@Serializable
data class UserPlaylistsPageResponse(
    val items: List<UserPlaylistDto?> = emptyList(),
    val next: String? = null,
)

@Serializable
data class SpotifySearchTracksDto(
    val items: List<SpotifyTrackDto> = emptyList(),
)

/** Ports app.py's search_spotify response shape (app.py:1600-1618). */
@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifySearchTracksDto = SpotifySearchTracksDto(),
)

/** Ports Spotify's /me/player/queue response shape — only `queue` is read, matching app.py's get_queue (app.py:1622-1663). */
@Serializable
data class SpotifyQueueResponse(
    val queue: List<SpotifyTrackDto> = emptyList(),
)

/** Ports app.py's get_top (app.py:2234-2274) — Signals screen's top-tracks tab. */
@Serializable
data class SpotifyTopTracksResponse(
    val items: List<SpotifyTrackDto> = emptyList(),
)

@Serializable
data class SpotifyFollowersDto(
    val total: Int? = null,
)

@Serializable
data class SpotifyTopArtistDto(
    val id: String = "",
    val name: String = "",
    val genres: List<String> = emptyList(),
    val images: List<SpotifyImageDto>? = null,
    // None (not 0) when Spotify omits followers entirely — /me/top/artists
    // doesn't always send it. Coercing to 0 here would show a misleading
    // "0 followers" instead of "—".
    val followers: SpotifyFollowersDto? = null,
) {
    val imageUrl: String? get() = images?.firstOrNull()?.url
}

/** Ports app.py's get_top (app.py:2234-2274) — Signals screen's top-artists tab. */
@Serializable
data class SpotifyTopArtistsResponse(
    val items: List<SpotifyTopArtistDto> = emptyList(),
)

@Serializable
data class RecentlyPlayedEntryDto(
    val track: SpotifyTrackDto? = null,
    @SerialName("played_at") val playedAt: String = "",
)

/** Ports app.py's recently_played (app.py:2169-2231) — Signals screen's recently-played tab. */
@Serializable
data class RecentlyPlayedResponse(
    val items: List<RecentlyPlayedEntryDto> = emptyList(),
)
