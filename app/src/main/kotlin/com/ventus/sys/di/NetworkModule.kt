package com.ventus.sys.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ventus.sys.data.remote.PlaybackApi
import com.ventus.sys.data.remote.ReccoBeatsApi
import com.ventus.sys.data.remote.SpotifyApi
import com.ventus.sys.data.remote.SpotifySearchApi
import com.ventus.sys.data.remote.SpotifyStatsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val SPOTIFY_BASE_URL = "https://api.spotify.com/v1/"
private const val RECCOBEATS_BASE_URL = "https://api.reccobeats.com/"
private const val TIMEOUT_SECONDS = 15L

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true // Spotify/ReccoBeats responses carry many fields neither app.py nor this app reads.
            isLenient = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                // BASIC (method/URL/status/duration), not BODY/HEADERS — real Spotify
                // responses can carry PII-adjacent data (email in some scopes,
                // listening history) that shouldn't land in logcat even in debug.
                level = HttpLoggingInterceptor.Level.BASIC
            }
        return OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideSpotifyRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideSpotifyApi(retrofit: Retrofit): SpotifyApi = retrofit.create(SpotifyApi::class.java)

    // Shares provideSpotifyRetrofit's instance rather than building its own -
    // same base URL/client/converter, no reason to duplicate the Builder call.
    @Provides
    @Singleton
    fun providePlaybackApi(retrofit: Retrofit): PlaybackApi = retrofit.create(PlaybackApi::class.java)

    // Signals screen's top tracks/artists/recently-played — same shared
    // retrofit instance, split into its own interface for the same
    // TooManyFunctions reason as PlaybackApi above.
    @Provides
    @Singleton
    fun provideSpotifyStatsApi(retrofit: Retrofit): SpotifyStatsApi = retrofit.create(SpotifyStatsApi::class.java)

    // Single-track lookup/search — split into its own interface for the same
    // TooManyFunctions reason as PlaybackApi/SpotifyStatsApi above.
    @Provides
    @Singleton
    fun provideSpotifySearchApi(retrofit: Retrofit): SpotifySearchApi = retrofit.create(SpotifySearchApi::class.java)

    @Provides
    @Singleton
    fun provideReccoBeatsApi(
        client: OkHttpClient,
        json: Json,
    ): ReccoBeatsApi =
        Retrofit
            .Builder()
            .baseUrl(RECCOBEATS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReccoBeatsApi::class.java)
}
