package com.ventus.sys.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * ReccoBeats does NOT echo the Spotify track ID directly on each item — the
 * ID is the last path segment of `href` (app.py:588-590's own comment on
 * fetch_reccobeats_batch). Some response versions DO include id/spotifyId/
 * trackId directly; all three are modeled as fallbacks, matching
 * app.py:611-618's tolerance exactly.
 */
@Serializable
data class ReccoBeatsFeatureDto(
    val href: String = "",
    val id: String? = null,
    val spotifyId: String? = null,
    val trackId: String? = null,
    val energy: Double = 0.0,
    val valence: Double = 0.0,
    val danceability: Double = 0.0,
    val tempo: Double = 0.0,
    val acousticness: Double = 0.0,
    val instrumentalness: Double = 0.0,
    val loudness: Double = 0.0,
    val key: Int? = null,
    val mode: Int? = null,
) {
    /** Spotify ID extracted from href's last path segment (the primary strategy — app.py:608-610). */
    val idFromHref: String?
        get() = href.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
}

@Serializable
data class ReccoBeatsBatchResponse(
    val content: List<ReccoBeatsFeatureDto> = emptyList(),
)
