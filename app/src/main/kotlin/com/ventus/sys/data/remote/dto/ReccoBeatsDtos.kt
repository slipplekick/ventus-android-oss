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
    // Nullable, not defaulted to 0.0 - a default only covers a genuinely
    // missing key. ReccoBeats can and does send an explicit JSON `null` for
    // individual fields on a degenerate/partial entry (e.g. a batch response
    // with "acousticness":null,"danceability":null on one track), and
    // kotlinx.serialization throws JsonDecodingException trying to decode a
    // JSON null into a non-nullable Double - an uncaught crash, not the
    // graceful "treat as missing" behavior this was supposed to have.
    // Repository layer coalesces null to 0.0 before looksLikeMissingData()
    // does its usual degenerate-entry filtering.
    val energy: Double? = null,
    val valence: Double? = null,
    val danceability: Double? = null,
    val tempo: Double? = null,
    val acousticness: Double? = null,
    val instrumentalness: Double? = null,
    val loudness: Double? = null,
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
