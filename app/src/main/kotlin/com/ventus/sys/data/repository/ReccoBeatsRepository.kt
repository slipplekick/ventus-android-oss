package com.ventus.sys.data.repository

import com.ventus.sys.data.remote.ReccoBeatsApi
import com.ventus.sys.data.remote.dto.ReccoBeatsBatchResponse
import com.ventus.sys.data.remote.dto.ReccoBeatsFeatureDto
import com.ventus.sys.domain.model.TrackFeatures
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_BATCH_SIZE = 40 // API silently drops extras beyond this — app.py:595-596.
private const val SCALE_THRESHOLD = 1.0
private const val SCALE_FACTOR = 100.0
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_RETRIES = 3
private const val BASE_BACKOFF_MS = 1000L

@Singleton
class ReccoBeatsRepository
    @Inject
    constructor(
        private val api: ReccoBeatsApi,
    ) {
        /**
         * Ports fetch_reccobeats_batch() exactly — app.py:593-646, incl. the
         * href-based ID extraction and the "scale x100 only if the API returned
         * a 0.0-1.0 float" defensive rescale (tempo/loudness/key/mode are
         * already in native units, never rescaled). Silently returns an empty
         * map on any failure — matches desktop's own behavior (a failed batch
         * just means those tracks stay unresolved, not a thrown exception the
         * poller/sync loop needs to handle specially).
         *
         * Retry-with-backoff on 429: ReccoBeats publishes no numeric rate
         * limit, so capped exponential backoff is used instead of a guessed
         * fixed delay.
         */
        suspend fun fetchBatch(trackIds: List<String>): Map<String, TrackFeatures> {
            if (trackIds.isEmpty()) return emptyMap()
            val batch = trackIds.take(MAX_BATCH_SIZE)
            val idSet = batch.toSet()

            val response = requestWithRetry(batch) ?: return emptyMap()
            if (!response.isSuccessful) return emptyMap()

            val content = response.body()?.content ?: return emptyMap()
            return content
                .mapNotNull { dto -> resolveId(dto, idSet)?.let { id -> id to dto.toDomain(id) } }
                .filterNot { (_, features) -> features.looksLikeMissingData() }
                .toMap()
        }

        private suspend fun requestWithRetry(batch: List<String>): Response<ReccoBeatsBatchResponse>? {
            val ids = batch.joinToString(",")
            var attempt = 0
            while (attempt < MAX_RETRIES) {
                val response =
                    try {
                        api.getAudioFeatures(ids)
                    } catch (
                        @Suppress("SwallowedException")
                        e: IOException,
                    ) {
                        // Deliberate: matches app.py's own `except: return {}` — a failed batch just
                        // means those tracks stay unresolved, not an exception the poller/sync loop
                        // needs to specially handle. Nothing useful to do with `e` at this boundary.
                        return null
                    }
                if (response.code() != HTTP_TOO_MANY_REQUESTS) return response
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(BASE_BACKOFF_MS * (1L shl attempt)) // 2s, 4s
                } else {
                    return response
                }
            }
            return null
        }

        private fun resolveId(
            dto: ReccoBeatsFeatureDto,
            requestedIds: Set<String>,
        ): String? {
            val fromHref = dto.idFromHref
            if (fromHref != null && fromHref in requestedIds) return fromHref
            val direct = dto.id ?: dto.spotifyId ?: dto.trackId
            if (direct != null && direct in requestedIds) return direct
            return null
        }
    }

private fun scaleIfFraction(value: Double): Double = if (value <= SCALE_THRESHOLD) value * SCALE_FACTOR else value

/**
 * Completeness guard, not a parity change: ReccoBeatsFeatureDto's fields are
 * nullable, coalesced to 0.0 in toDomain() (matching app.py's own
 * `raw.get('energy', 0) or 0`, app.py:624-628, which treats missing-or-null
 * the same way) - but that coalescing alone can't tell "this track scored 0"
 * from "ReccoBeats sent a
 * degenerate/partial entry" apart. Auditing 194 real synced tracks this
 * session found zero cases where energy/valence/dance/acoustic/instrumental
 * were ALL exactly 0.0 together (real tracks always vary across at least
 * one axis, even quiet/ambient ones) - so that specific combination is a
 * safe, non-breaking signal of a bad entry, not a real track's honest
 * score. Caught here means it's dropped instead of cached as if it were
 * real data (FeatureResolverImpl then reports it UNRESOLVED, same as any
 * other track ReccoBeats has no data for).
 */
private fun TrackFeatures.looksLikeMissingData(): Boolean =
    energy == 0.0 && valence == 0.0 && danceability == 0.0 && acousticness == 0.0 && instrumentalness == 0.0

private fun ReccoBeatsFeatureDto.toDomain(resolvedId: String) =
    TrackFeatures(
        id = resolvedId,
        energy = scaleIfFraction(energy ?: 0.0),
        valence = scaleIfFraction(valence ?: 0.0),
        danceability = scaleIfFraction(danceability ?: 0.0),
        bpm = tempo ?: 0.0,
        acousticness = scaleIfFraction(acousticness ?: 0.0),
        instrumentalness = scaleIfFraction(instrumentalness ?: 0.0),
        loudness = loudness ?: 0.0,
        key = key ?: -1,
        mode = mode ?: 1,
    )
