package com.ventus.sys.ui.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.remote.dto.SpotifyTrackDto
import com.ventus.sys.data.repository.RecentlyPlayedTrack
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.data.repository.VaultRepository
import com.ventus.sys.domain.ScoreEngine
import com.ventus.sys.domain.model.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

private const val TOP_LIMIT = 50
private const val RECENT_LIMIT = 50
private const val HIGH_SCORE_THRESHOLD = 80

/** app.js's three Spotify time-range buckets (app.js:1963), unchanged. */
enum class SignalsRange(
    val apiValue: String,
    val label: String,
) {
    SHORT_TERM("short_term", "LAST 4 WEEKS"),
    MEDIUM_TERM("medium_term", "LAST 6 MONTHS"),
    LONG_TERM("long_term", "ALL TIME"),
}

data class TopTrackUiItem(
    val id: String,
    val rank: Int,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val durationMs: Long,
)

data class TopArtistUiItem(
    val id: String,
    val rank: Int,
    val name: String,
    val genres: String,
    val imageUrl: String?,
    val followers: Long?,
)

data class RecentTrackUiItem(
    val id: String,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val playedAt: String,
    val score: Int?,
    val verdict: Verdict?,
    val isHighScore: Boolean,
)

sealed interface SignalsSectionState<out T> {
    data object Loading : SignalsSectionState<Nothing>

    data class Error(
        val message: String,
    ) : SignalsSectionState<Nothing>

    data class Loaded<T>(
        val items: List<T>,
    ) : SignalsSectionState<T>
}

data class SignalsUiState(
    val range: SignalsRange = SignalsRange.SHORT_TERM,
    val tracks: SignalsSectionState<TopTrackUiItem> = SignalsSectionState.Loading,
    val artists: SignalsSectionState<TopArtistUiItem> = SignalsSectionState.Loading,
    val recent: SignalsSectionState<RecentTrackUiItem> = SignalsSectionState.Loading,
)

/** Ports app.js's Signals page (loadSignals, app.js:1916-2043) / app.py's get_top + recently_played. */
@HiltViewModel
class SignalsViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyRepository,
        private val vaultRepository: VaultRepository,
        private val tasteProfileRepository: TasteProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignalsUiState())
        val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

        init {
            load(SignalsRange.SHORT_TERM)
        }

        fun setRange(range: SignalsRange) {
            if (range == _uiState.value.range && _uiState.value.tracks !is SignalsSectionState.Error) return
            load(range)
        }

        fun load(range: SignalsRange = _uiState.value.range) {
            _uiState.value =
                SignalsUiState(
                    range = range,
                    tracks = SignalsSectionState.Loading,
                    artists = SignalsSectionState.Loading,
                    recent = SignalsSectionState.Loading,
                )
            viewModelScope.launch {
                coroutineScope {
                    val tracksDeferred = async { loadTopTracks(range) }
                    val artistsDeferred = async { loadTopArtists(range) }
                    val recentDeferred = async { loadRecent() }
                    _uiState.value = _uiState.value.copy(tracks = tracksDeferred.await())
                    _uiState.value = _uiState.value.copy(artists = artistsDeferred.await())
                    _uiState.value = _uiState.value.copy(recent = recentDeferred.await())
                }
            }
        }

        private suspend fun loadTopTracks(range: SignalsRange): SignalsSectionState<TopTrackUiItem> =
            try {
                val tracks: List<SpotifyTrackDto> = spotifyRepository.getTopTracks(range.apiValue, TOP_LIMIT)
                SignalsSectionState.Loaded(
                    tracks.mapIndexed { index, t ->
                        TopTrackUiItem(
                            id = t.id ?: "$index-${t.name}",
                            rank = index + 1,
                            name = t.name,
                            artist = t.artistName,
                            albumArtUrl = t.albumArtUrl,
                            durationMs = t.durationMs,
                        )
                    },
                )
            } catch (e: IOException) {
                SignalsSectionState.Error(e.message ?: "Failed to fetch top tracks")
            } catch (e: HttpException) {
                SignalsSectionState.Error("Failed to fetch top tracks (${e.code()})")
            }

        private suspend fun loadTopArtists(range: SignalsRange): SignalsSectionState<TopArtistUiItem> =
            try {
                val artists = spotifyRepository.getTopArtists(range.apiValue, TOP_LIMIT)
                SignalsSectionState.Loaded(
                    artists.mapIndexed { index, a ->
                        TopArtistUiItem(
                            id = a.id,
                            rank = index + 1,
                            name = a.name,
                            genres = a.genres.take(2).joinToString(" · "),
                            imageUrl = a.imageUrl,
                            followers = a.followers?.total?.toLong(),
                        )
                    },
                )
            } catch (e: IOException) {
                SignalsSectionState.Error(e.message ?: "Failed to fetch top artists")
            } catch (e: HttpException) {
                SignalsSectionState.Error("Failed to fetch top artists (${e.code()})")
            }

        private suspend fun loadRecent(): SignalsSectionState<RecentTrackUiItem> =
            try {
                val recent: List<RecentlyPlayedTrack> = spotifyRepository.getRecentlyPlayed(RECENT_LIMIT)
                val ids = recent.map { it.id }
                val features = vaultRepository.resolveMany(ids)
                val profile = tasteProfileRepository.observeProfile().first()
                SignalsSectionState.Loaded(
                    recent.map { t ->
                        val feature = features[t.id]
                        val result = feature?.let { ScoreEngine.score(it, profile) }
                        RecentTrackUiItem(
                            id = t.id,
                            name = t.name,
                            artist = t.artist,
                            albumArtUrl = t.albumArtUrl,
                            playedAt = t.playedAt,
                            score = result?.score,
                            verdict = result?.verdict,
                            isHighScore = (result?.score ?: 0) >= HIGH_SCORE_THRESHOLD,
                        )
                    },
                )
            } catch (e: IOException) {
                SignalsSectionState.Error(e.message ?: "Could not load recently played.")
            } catch (e: HttpException) {
                val message = if (e.code() == HTTP_FORBIDDEN) MISSING_SCOPE_MESSAGE else "Could not load recently played (${e.code()})"
                SignalsSectionState.Error(message)
            }

        companion object {
            private const val HTTP_FORBIDDEN = 403
            private const val MISSING_SCOPE_MESSAGE =
                "Missing permission — log out and back in to grant 'recently played' access."
        }
    }
