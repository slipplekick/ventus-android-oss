package com.ventus.sys.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.data.repository.VaultRepository
import com.ventus.sys.domain.Camelot
import com.ventus.sys.domain.ScoreEngine
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

private const val QUEUE_LIMIT = 15 // app.py:1631's own `[:15]` slice.

data class QueueTrackUiItem(
    val id: String,
    val name: String,
    val artist: String,
    val camelot: String,
    val energy: Int?,
    val valence: Int?,
    val score: Int?,
    val verdict: Verdict?,
)

sealed interface QueueUiState {
    data object Loading : QueueUiState

    data class Error(
        val message: String,
    ) : QueueUiState

    data class Loaded(
        val tracks: List<QueueTrackUiItem>,
        val lastSyncedAtMs: Long,
    ) : QueueUiState
}

/** Ports app.js's Queue Analyzer page (loadQueue, app.js:1852-1904) / app.py's get_queue (app.py:1622-1663). */
@HiltViewModel
class QueueViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyRepository,
        private val vaultRepository: VaultRepository,
        private val tasteProfileRepository: TasteProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<QueueUiState>(QueueUiState.Loading)
        val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            _uiState.value = QueueUiState.Loading
            viewModelScope.launch {
                val queue =
                    try {
                        spotifyRepository.getQueue()
                    } catch (e: IOException) {
                        _uiState.value = QueueUiState.Error(e.message ?: "Failed to fetch queue")
                        return@launch
                    } catch (e: HttpException) {
                        _uiState.value = QueueUiState.Error("Failed to fetch queue (${e.code()})")
                        return@launch
                    }

                if (queue == null) {
                    _uiState.value = QueueUiState.Error("No active Spotify player. Start playing something first.")
                    return@launch
                }

                val limited = queue.take(QUEUE_LIMIT)
                val ids = limited.mapNotNull { it.id }
                val features = vaultRepository.resolveMany(ids)
                val profile = tasteProfileRepository.observeProfile().first()

                val items =
                    limited.mapNotNull { track ->
                        val id = track.id ?: return@mapNotNull null
                        val feature = features[id]
                        val result = feature?.let { ScoreEngine.score(it, profile) }
                        QueueTrackUiItem(
                            id = id,
                            name = track.name,
                            artist = track.artistName,
                            camelot = feature?.let { Camelot.get(it.key, it.mode) } ?: "--",
                            energy = feature?.energy?.toInt(),
                            valence = feature?.valence?.toInt(),
                            score = result?.score,
                            verdict = result?.verdict,
                        )
                    }
                _uiState.value = QueueUiState.Loaded(items, System.currentTimeMillis())
            }
        }
    }
