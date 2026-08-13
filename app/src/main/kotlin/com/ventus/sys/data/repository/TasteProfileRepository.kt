package com.ventus.sys.data.repository

import com.ventus.sys.data.local.ProfileTrackDao
import com.ventus.sys.data.local.ProfileTrackEntity
import com.ventus.sys.data.remote.dto.SpotifyTrackDto
import com.ventus.sys.domain.TasteProfileCalculator
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Display name for a track — mirrors app.js's renderVault() nameMap (app.js:520-521). */
data class TrackName(
    val song: String,
    val artist: String,
)

sealed interface SyncResult {
    data class Success(
        val added: Int,
        val removed: Int,
    ) : SyncResult

    data class Refused(
        val reason: String,
    ) : SyncResult

    data class Failed(
        val error: String,
    ) : SyncResult
}

/**
 * Owns the taste-profile playlist's track list (profile_tracks table) and
 * the sync/diff logic that keeps it matching the source Spotify playlist —
 * ports _sync_playlist_core() (app.py:1113-1280), including the
 * mass-deletion safety guard (app.py:1176-1183): a sync that would remove
 * over half of an established (>10-track) profile in one shot is refused
 * outright rather than silently wiping real data — this happened for real
 * during the original desktop session per app.py's own comment.
 */
@Singleton
class TasteProfileRepository
    @Inject
    constructor(
        private val profileTrackDao: ProfileTrackDao,
        private val spotifyRepository: SpotifyRepository,
        private val reccoBeatsRepository: ReccoBeatsRepository,
        private val vaultRepository: VaultRepository,
    ) {
        companion object {
            private const val MASS_DELETE_MIN_EXISTING = 10
            private const val MASS_DELETE_FRACTION_THRESHOLD = 0.5
            private const val RECCOBEATS_BATCH_SIZE = 40
        }

        fun observeProfile(): Flow<TasteProfile> =
            profileTrackDao.observeAll().map { rows -> TasteProfileCalculator.calculate(rows.map { it.toInputRow() }) }

        /**
         * Song/artist names for every track currently in the taste-profile
         * playlist — the Track Vault screen merges this with the raw vault
         * feature cache the same way app.js's renderVault() does
         * (app.js:520-521). Vault entries with no match here (never part of
         * a synced playlist — Discover/Queue lookups) fall back to a
         * truncated-ID placeholder rather than a lazy per-track Spotify name
         * lookup; app.js does that lazily (fetchVaultName, app.js:494-507)
         * but that's deferred here unless vault-only entries turn out to be
         * common enough in practice for it to matter.
         */
        fun observeTrackNames(): Flow<Map<String, TrackName>> =
            profileTrackDao.observeAll().map { rows -> rows.associate { it.id to TrackName(it.song, it.artist) } }

        /** Raw per-track features for the taste-profile playlist — Dashboard's KPI/histogram/radar source. */
        fun observeTrackFeatures(): Flow<List<TrackFeatures>> =
            profileTrackDao.observeAll().map { rows -> rows.map { it.toTrackFeatures() } }

        suspend fun syncPlaylist(playlistId: String): SyncResult {
            val remoteTracks =
                try {
                    spotifyRepository.getPlaylistTracks(playlistId)
                } catch (e: IOException) {
                    return SyncResult.Failed(e.message ?: "Spotify fetch failed")
                } catch (e: HttpException) {
                    // Retrofit throws HttpException (a RuntimeException, NOT an
                    // IOException) for non-2xx responses - a bad/private/deleted
                    // playlist ID (very plausible for user-typed input) returns
                    // 404 this way, which the IOException catch above misses
                    // entirely and would otherwise crash the sync uncaught.
                    return SyncResult.Failed(playlistFetchErrorMessage(e))
                }
            val remoteIds = remoteTracks.mapNotNull { it.id }.toSet()
            val existingIds = profileTrackDao.getAllIds().toSet()
            val removedIds = existingIds - remoteIds

            if (isMassDeletion(existingIds.size, removedIds.size)) {
                return SyncResult.Refused(
                    "Refusing to sync: this would remove ${removedIds.size} of ${existingIds.size} " +
                        "tracks from the profile in one go — almost certainly the wrong playlist.",
                )
            }
            if (removedIds.isNotEmpty()) {
                // profile_tracks only, deliberately — NOT vaultRepository. The
                // shared vault feature cache outlives any single playlist's
                // membership (a track removed from the profile playlist can
                // still be cached there via Master Vault or a prior Discover/
                // Queue lookup); deleting it here would silently corrupt any
                // overlapping screen. Same invariant MasterVaultRepository.clear()
                // already documents and follows.
                profileTrackDao.deleteAll(removedIds.toList())
            }

            val newTracks = remoteTracks.filter { it.id != null && it.id !in existingIds }
            if (newTracks.isEmpty()) return SyncResult.Success(added = 0, removed = removedIds.size)

            insertResolvedTracks(newTracks)
            return SyncResult.Success(added = newTracks.size, removed = removedIds.size)
        }

        private fun isMassDeletion(
            existingCount: Int,
            removedCount: Int,
        ): Boolean =
            existingCount > MASS_DELETE_MIN_EXISTING &&
                removedCount.toDouble() / existingCount > MASS_DELETE_FRACTION_THRESHOLD

        /**
         * Only tracks ReccoBeats actually resolved get inserted into
         * profile_tracks — a track it has no data for is skipped entirely,
         * not written as an all-zero row. TasteProfileCalculator computes a
         * plain weighted mean with no per-row filtering (by design — see its
         * own doc comment: Android's vault always carries a real value for
         * every metric on every row, unlike desktop's per-field nullability).
         * An all-zero ghost row would silently violate that and drag the
         * whole profile (BPM/loudness especially, both unrescaled weighted
         * means) toward 0, corrupting every ScoreEngine.score() call across
         * every screen.
         */
        private suspend fun insertResolvedTracks(newTracks: List<SpotifyTrackDto>) {
            val today = LocalDate.now()
            val tracksById = newTracks.filter { it.id != null }.associateBy { it.id!! }
            val newIds = tracksById.keys.toList()
            val rows = mutableListOf<ProfileTrackEntity>()
            val resolvedFeatures = mutableListOf<TrackFeatures>()

            for (start in newIds.indices step RECCOBEATS_BATCH_SIZE) {
                val chunk = newIds.subList(start, minOf(start + RECCOBEATS_BATCH_SIZE, newIds.size))
                val features = reccoBeatsRepository.fetchBatch(chunk)
                for (id in chunk) {
                    val feat = features[id] ?: continue
                    rows += tracksById.getValue(id).toProfileTrackEntity(id, today, feat)
                    resolvedFeatures += feat
                }
            }

            profileTrackDao.insertAll(rows)
            if (resolvedFeatures.isNotEmpty()) vaultRepository.upsert(resolvedFeatures)
        }

        suspend fun wipeProfile() {
            profileTrackDao.clear()
        }
    }

private fun ProfileTrackEntity.toTrackFeatures() =
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

private fun ProfileTrackEntity.toInputRow() =
    TasteProfileCalculator.ProfileInputRow(
        energy = energy,
        valence = valence,
        danceability = danceability,
        bpm = bpm,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        loudness = loudness,
        addedAt = addedAt,
    )

private fun SpotifyTrackDto.toProfileTrackEntity(
    id: String,
    addedAt: LocalDate,
    feat: TrackFeatures,
) = ProfileTrackEntity(
    id = id,
    song = name,
    artist = artistName,
    addedAt = addedAt,
    energy = feat.energy,
    valence = feat.valence,
    danceability = feat.danceability,
    bpm = feat.bpm,
    acousticness = feat.acousticness,
    instrumentalness = feat.instrumentalness,
    loudness = feat.loudness,
    key = feat.key,
    mode = feat.mode,
)
