package com.ventus.sys.data.repository

import com.ventus.sys.domain.FeatureResolver
import com.ventus.sys.domain.ResolutionMethod
import com.ventus.sys.domain.ResolvedFeatures
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The concrete 3-tier resolver FeatureResolver's domain-level doc comment
 * describes: vault cache -> ReccoBeats batch (single-track here; callers
 * needing real batching call ReccoBeatsRepository.fetchBatch directly, e.g.
 * a playlist sync) -> mark unresolved. Lives in the data layer because it
 * needs Room + Retrofit, which the domain layer must stay free of.
 */
@Singleton
class FeatureResolverImpl
    @Inject
    constructor(
        private val vaultRepository: VaultRepository,
        private val reccoBeatsRepository: ReccoBeatsRepository,
    ) : FeatureResolver {
        override suspend fun resolve(trackId: String): ResolvedFeatures {
            vaultRepository.get(trackId)?.let { cached ->
                return ResolvedFeatures(cached, ResolutionMethod.VAULT_CACHE)
            }

            val fetched = reccoBeatsRepository.fetchBatch(listOf(trackId))[trackId]
            if (fetched != null) {
                vaultRepository.upsert(listOf(fetched))
                return ResolvedFeatures(fetched, ResolutionMethod.RECCOBEATS_API)
            }

            return ResolvedFeatures(null, ResolutionMethod.UNRESOLVED)
        }
    }
