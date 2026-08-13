package com.ventus.sys.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.domain.Camelot
import com.ventus.sys.domain.FeatureResolver
import com.ventus.sys.domain.NeighborDistance
import com.ventus.sys.domain.ScoreEngine
import com.ventus.sys.domain.model.TasteProfile
import com.ventus.sys.domain.model.TrackFeatures
import com.ventus.sys.domain.model.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import kotlin.math.roundToInt

private const val NEIGHBOR_LIMIT = 8
private const val ID_PREVIEW_LENGTH = 10

data class DiscoverSearchResult(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val isScanning: Boolean = false,
    val scanned: Boolean = false,
    val score: Int? = null,
    val verdict: Verdict? = null,
)

data class NeighborUiItem(
    val id: String,
    val song: String,
    val artist: String,
    val camelot: String,
    val energy: Int,
    val valence: Int,
    val bpm: Int,
    val score: Int,
    val verdict: Verdict,
    val distance: Int,
)

/**
 * Ports app.js's Discover page: search (runDiscoverSearch/search_spotify,
 * app.js:725-756, app.py:1600-1618), scan-to-score (scoreDiscoverTrack,
 * app.js:758-780, reuses the same single-track resolve path as the Live
 * Scorer), and the neighbor ranking (renderNeighbors, app.js:672-696,
 * ported as [NeighborDistance]). "Neighbors" are ranked against the
 * taste-profile playlist's own tracks (the same set Dashboard's KPIs use),
 * matching app.js's own `tracks` array — not the full global vault.
 */
@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyRepository,
        private val featureResolver: FeatureResolver,
        private val tasteProfileRepository: TasteProfileRepository,
    ) : ViewModel() {
        val searchQuery = MutableStateFlow("")

        private val _isSearching = MutableStateFlow(false)
        val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

        private val _searchError = MutableStateFlow<String?>(null)
        val searchError: StateFlow<String?> = _searchError.asStateFlow()

        private val _searchResults = MutableStateFlow<List<DiscoverSearchResult>>(emptyList())
        val searchResults: StateFlow<List<DiscoverSearchResult>> = _searchResults.asStateFlow()

        private val _neighbors = MutableStateFlow<List<NeighborUiItem>>(emptyList())
        val neighbors: StateFlow<List<NeighborUiItem>> = _neighbors.asStateFlow()

        fun search() {
            val query = searchQuery.value.trim()
            if (query.isEmpty()) return
            _isSearching.value = true
            _searchError.value = null
            // A previous scanTrack() call's neighbor list would otherwise stay
            // visible under this new, unrelated search's results (the
            // screen shows it purely on isNotEmpty, with nothing tying it to
            // which search produced it) - reading as if those rows belong to
            // the new search.
            _neighbors.value = emptyList()
            viewModelScope.launch {
                try {
                    val results = spotifyRepository.search(query)
                    _searchResults.value =
                        results.mapNotNull { track ->
                            track.id?.let {
                                DiscoverSearchResult(
                                    id = it,
                                    name = track.name,
                                    artist = track.artistName,
                                    album = track.album.name,
                                    albumArtUrl = track.albumArtUrl,
                                )
                            }
                        }
                } catch (e: IOException) {
                    _searchError.value = e.message ?: "Search failed"
                } catch (e: HttpException) {
                    _searchError.value = "Search failed (${e.code()})"
                } finally {
                    _isSearching.value = false
                }
            }
        }

        /** Ports app.js's scoreDiscoverTrack (app.js:758-780) — scan one search result, then rebuild neighbors around it. */
        fun scanTrack(trackId: String) {
            updateResult(trackId) { it.copy(isScanning = true) }
            viewModelScope.launch {
                val resolved = featureResolver.resolve(trackId)
                val features = resolved.features
                val profile = tasteProfileRepository.observeProfile().first()
                if (features == null) {
                    updateResult(trackId) { it.copy(isScanning = false, scanned = true, score = null, verdict = null) }
                    return@launch
                }
                val result = ScoreEngine.score(features, profile)
                updateResult(trackId) {
                    it.copy(isScanning = false, scanned = true, score = result.score, verdict = result.verdict)
                }
                buildNeighbors(features, profile)
            }
        }

        private fun updateResult(
            trackId: String,
            transform: (DiscoverSearchResult) -> DiscoverSearchResult,
        ) {
            _searchResults.value = _searchResults.value.map { if (it.id == trackId) transform(it) else it }
        }

        private suspend fun buildNeighbors(
            target: TrackFeatures,
            profile: TasteProfile,
        ) {
            val libraryTracks = tasteProfileRepository.observeTrackFeatures().first()
            val names = tasteProfileRepository.observeTrackNames().first()
            _neighbors.value =
                NeighborDistance.nearest(target, libraryTracks, NEIGHBOR_LIMIT).map { feature ->
                    val result = ScoreEngine.score(feature, profile)
                    val name = names[feature.id]
                    NeighborUiItem(
                        id = feature.id,
                        song = name?.song ?: (feature.id.take(ID_PREVIEW_LENGTH) + "…"),
                        artist = name?.artist ?: "UNKNOWN",
                        camelot = Camelot.get(feature.key, feature.mode),
                        energy = feature.energy.toInt(),
                        valence = feature.valence.toInt(),
                        bpm = feature.bpm.toInt(),
                        score = result.score,
                        verdict = result.verdict,
                        distance = NeighborDistance.distance(feature, target).roundToInt(),
                    )
                }
        }
    }
