package com.ventus.sys.data.remote

import com.ventus.sys.data.remote.dto.ReccoBeatsBatchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * ReccoBeats' batch audio-features endpoint. No auth needed (public API).
 * Retry-with-backoff on 429 lives in the repository layer — ReccoBeats
 * publishes no numeric rate limit, so backoff-on-429 is the only correct
 * strategy, not a guessed fixed rate.
 */
interface ReccoBeatsApi {
    @GET("v1/audio-features")
    suspend fun getAudioFeatures(
        @Query("ids") commaSeparatedIds: String,
    ): Response<ReccoBeatsBatchResponse>
}
