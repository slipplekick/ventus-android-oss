package com.ventus.sys.domain

import com.ventus.sys.domain.model.TrackFeatures

/** Where a [TrackFeatures] result came from — surfaced in the UI (e.g. Live Scorer's method label). */
enum class ResolutionMethod {
    VAULT_CACHE,
    RECCOBEATS_API,
    UNRESOLVED,
}

data class ResolvedFeatures(
    val features: TrackFeatures?,
    val method: ResolutionMethod,
)

/**
 * Domain-level contract for turning a Spotify track ID into resolved audio
 * features. The concrete implementation (VaultRepository) needs Room +
 * Retrofit — real dependencies the domain layer itself must stay free of
 * ("plain Kotlin, zero Android imports"). Defining the interface here and
 * implementing it in the data layer keeps that boundary honest instead of
 * leaking network/DB types into domain/.
 *
 * Three resolution tiers: vault cache -> ReccoBeats batch -> mark
 * unresolved. Spotify's own /audio-features and /audio-analysis endpoints
 * are restricted for most developer apps as of a late-2024 API policy
 * change, so ReccoBeats is the primary audio-feature source here — it alone
 * resolves the large majority of real-world tracks.
 */
interface FeatureResolver {
    suspend fun resolve(trackId: String): ResolvedFeatures
}
