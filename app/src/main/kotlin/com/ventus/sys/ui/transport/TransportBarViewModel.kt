package com.ventus.sys.ui.transport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.PlaybackRepository
import com.ventus.sys.service.NowPlayingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TICK_INTERVAL_MS = 1000L

data class TransportUiState(
    val isPlaying: Boolean = false,
    val trackName: String? = null,
    val artist: String? = null,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Ports app.js's transport deck (app.js:1372-1430: sendPlayback/
 * togglePlayPause + the 1s local progress ticker that keeps the seek bar
 * moving smoothly between [com.ventus.sys.service.NowPlayingService]'s 3s
 * polls). Requests here go straight to Spotify's Web API via
 * [PlaybackRepository] - same pattern every other screen already uses, no
 * separate backend hop needed (unlike desktop, which proxies through app.py).
 *
 * Play/pause is applied optimistically to the shared [NowPlayingStateHolder]
 * (not just local ViewModel state) so every screen showing playback state
 * - not just wherever the transport bar is mounted - reflects the tap
 * immediately instead of waiting up to 3s for the next poll to correct it.
 */
@HiltViewModel
class TransportBarViewModel
    @Inject
    constructor(
        private val stateHolder: NowPlayingStateHolder,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TransportUiState())
        val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

        /** True while the user is dragging the seek bar — suppresses both the poll and the local ticker until released. */
        var isSeekDragging: Boolean = false

        private var tickerJob: Job? = null

        init {
            viewModelScope.launch {
                stateHolder.state.collect { s ->
                    _uiState.value =
                        TransportUiState(
                            isPlaying = s.isPlaying,
                            trackName = s.trackName,
                            artist = s.artist,
                            progressMs = s.progressMs,
                            durationMs = s.durationMs,
                        )
                    if (s.isPlaying) startTicker() else stopTicker()
                }
            }
        }

        private fun startTicker() {
            if (tickerJob?.isActive == true) return
            tickerJob =
                viewModelScope.launch {
                    while (true) {
                        delay(TICK_INTERVAL_MS)
                        if (!isSeekDragging) {
                            _uiState.value =
                                _uiState.value.let { s ->
                                    s.copy(progressMs = (s.progressMs + TICK_INTERVAL_MS).coerceAtMost(s.durationMs))
                                }
                        }
                    }
                }
        }

        private fun stopTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        fun togglePlayPause() {
            val playing = _uiState.value.isPlaying
            _uiState.value = _uiState.value.copy(isPlaying = !playing)
            stateHolder.update { it.copy(isPlaying = !playing) }
            viewModelScope.launch {
                val ok = if (playing) playbackRepository.pause() else playbackRepository.play()
                if (!ok) {
                    // Revert the optimistic flip — most likely cause is no active
                    // Spotify device (phone's Spotify closed, screen off elsewhere).
                    _uiState.value = _uiState.value.copy(isPlaying = playing)
                    stateHolder.update { it.copy(isPlaying = playing) }
                }
            }
        }

        fun skipNext() {
            viewModelScope.launch { playbackRepository.skipNext() }
        }

        fun skipPrevious() {
            viewModelScope.launch { playbackRepository.skipPrevious() }
        }

        /** Called once on release, not per drag frame — matches desktop's seekEnd, not a live per-pixel API call. */
        fun seekTo(positionMs: Long) {
            _uiState.value = _uiState.value.copy(progressMs = positionMs)
            stateHolder.update { it.copy(progressMs = positionMs) }
            viewModelScope.launch { playbackRepository.seek(positionMs) }
        }

        override fun onCleared() {
            stopTicker()
        }
    }
