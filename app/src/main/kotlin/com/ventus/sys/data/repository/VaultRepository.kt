package com.ventus.sys.data.repository

import com.ventus.sys.data.local.ResolvedNameDao
import com.ventus.sys.data.local.ResolvedNameEntity
import com.ventus.sys.data.local.VaultDao
import com.ventus.sys.data.local.VaultEntity
import com.ventus.sys.domain.model.TrackFeatures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

private val PROTECTED_SOURCES = setOf("local_engine", "spotify_af", "audio_analysis")
private const val RESOLVE_BATCH_SIZE = 40 // API silently drops extras beyond this — app.py:595-596.

@Singleton
class VaultRepository
    @Inject
    constructor(
        private val vaultDao: VaultDao,
        private val resolvedNameDao: ResolvedNameDao,
        private val spotifyRepository: SpotifyRepository,
        private val reccoBeatsRepository: ReccoBeatsRepository,
    ) {
        // In-flight guard, not persisted - a track can only be "currently being
        // fetched" for as long as this process is alive; on restart there's
        // nothing pending to protect against re-fetching.
        private val namesBeingResolved = Collections.synchronizedSet(mutableSetOf<String>())

        suspend fun get(id: String): TrackFeatures? = vaultDao.get(id)?.toDomain()

        suspend fun getMany(ids: List<String>): Map<String, TrackFeatures> = vaultDao.getMany(ids).associate { it.id to it.toDomain() }

        fun observeAll(): Flow<List<TrackFeatures>> = vaultDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        suspend fun count(): Int = vaultDao.count()

        /** Lazily-resolved vault-only names ([ResolvedNameEntity]) — merged with playlist names by the Vault screen. */
        fun observeResolvedNames(): Flow<Map<String, TrackName>> =
            resolvedNameDao.observeAll().map { rows -> rows.associate { it.id to TrackName(it.song, it.artist) } }

        /**
         * Ports app.js's fetchVaultName (app.js:494-507): fetch a track's real
         * name from Spotify and cache it, for a vault entry with no name from
         * any other source. namesBeingResolved prevents duplicate concurrent
         * fetches for the same ID - the Vault screen's combine() re-evaluates
         * on every emission (including the one this very fetch causes once it
         * completes), so without this guard every still-unresolved entry
         * would re-trigger a fresh fetch on every recomposition.
         */
        suspend fun resolveNameIfMissing(id: String) {
            if (!namesBeingResolved.add(id)) return
            try {
                val track = spotifyRepository.getTrack(id) ?: return
                resolvedNameDao.upsert(ResolvedNameEntity(id = id, song = track.name, artist = track.artistName))
            } finally {
                namesBeingResolved.remove(id)
            }
        }

        /**
         * Ports vault_insert()'s protected-source rule exactly — app.py:275-327.
         * A 'reccobeats' write never silently downgrades a row whose source is
         * already local_engine/spotify_af/audio_analysis ("Ghost Signal data is
         * precious"). Android only ever calls this with source='reccobeats' in
         * practice (see FeatureResolverImpl), but the check runs regardless so
         * an imported desktop vault export with protected rows can't be
         * corrupted by a later Android-side resolve.
         */
        suspend fun upsert(
            rows: List<TrackFeatures>,
            source: String = "reccobeats",
        ) {
            if (rows.isEmpty()) return
            val entities = rows.map { it.toEntity(source) }

            val protectedRows = entities.filter { it.source in PROTECTED_SOURCES }
            val candidateRows = entities.filter { it.source !in PROTECTED_SOURCES }

            if (protectedRows.isNotEmpty()) {
                vaultDao.insertAll(protectedRows)
            }
            if (candidateRows.isNotEmpty()) {
                val existingProtectedIds = vaultDao.getProtectedIds(candidateRows.map { it.id }).toSet()
                val safeRows = candidateRows.filter { it.id !in existingProtectedIds }
                if (safeRows.isNotEmpty()) {
                    vaultDao.insertAll(safeRows)
                }
            }
        }

        suspend fun deleteAll(ids: List<String>) {
            if (ids.isNotEmpty()) vaultDao.deleteAll(ids)
        }

        /**
         * Bulk resolve: vault cache first, then batch ReccoBeats (chunks of
         * [RESOLVE_BATCH_SIZE]) for anything uncached, caching newly-resolved
         * features as it goes. Ports the batching app.py's audit_playlist/
         * sync_vault_from_playlist both use (app.py:1489-1499, app.py:1554-
         * 1561) — bulk vault lookup + batched ReccoBeats calls, not one
         * ReccoBeats request per track (which is what FeatureResolverImpl's
         * single-track resolve() does, correctly, for the live-scorer case
         * where only one track needs resolving at a time; a whole-playlist
         * audit needs this batched version instead or it'd make one HTTP
         * round-trip per track in the playlist).
         */
        suspend fun resolveMany(ids: List<String>): Map<String, TrackFeatures> {
            val cached = getMany(ids)
            val uncachedIds = ids.filter { it !in cached }
            if (uncachedIds.isEmpty()) return cached

            val resolved = cached.toMutableMap()
            for (start in uncachedIds.indices step RESOLVE_BATCH_SIZE) {
                val chunk = uncachedIds.subList(start, minOf(start + RESOLVE_BATCH_SIZE, uncachedIds.size))
                val batch = reccoBeatsRepository.fetchBatch(chunk)
                if (batch.isNotEmpty()) {
                    upsert(batch.values.toList())
                    resolved += batch
                }
            }
            return resolved
        }
    }

private fun VaultEntity.toDomain() =
    TrackFeatures(
        id = id,
        energy = energy,
        valence = valence,
        danceability = danceability,
        bpm = bpm,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        loudness = loudness,
        key = key,
        mode = mode,
    )

private fun TrackFeatures.toEntity(source: String) =
    VaultEntity(
        id = id,
        energy = energy,
        valence = valence,
        danceability = danceability,
        bpm = bpm,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        loudness = loudness,
        key = key,
        mode = mode,
        source = source,
    )
