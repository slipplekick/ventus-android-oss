package com.ventus.sys.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.local.AppPreferences
import com.ventus.sys.data.remote.dto.UserPlaylistDto
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.SyncResult
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.domain.extractSpotifyId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Ports app.js's first-run overlay (app.js:2306-2493) — playlist picker
 * (rich cards w/ art), Liked Songs as a distinct clearly-labeled
 * alternative (not mixed into the playlist list — a deliberate desktop UX
 * choice, app.js:577-580's comment, kept here), manual paste, and skip.
 */
sealed interface OnboardingStep {
    data object LoadingPlaylists : OnboardingStep

    data class PickerReady(
        val playlists: List<UserPlaylistDto>,
    ) : OnboardingStep

    data class LoadFailed(
        val message: String,
    ) : OnboardingStep

    data object Syncing : OnboardingStep

    data class SyncFailed(
        val message: String,
    ) : OnboardingStep

    data object Done : OnboardingStep
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyRepository,
        private val tasteProfileRepository: TasteProfileRepository,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _step = MutableStateFlow<OnboardingStep>(OnboardingStep.LoadingPlaylists)
        val step: StateFlow<OnboardingStep> = _step.asStateFlow()

        init {
            loadPlaylists()
        }

        fun loadPlaylists() {
            _step.value = OnboardingStep.LoadingPlaylists
            viewModelScope.launch {
                try {
                    val playlists = spotifyRepository.getUserPlaylists()
                    _step.value = OnboardingStep.PickerReady(playlists)
                } catch (e: IOException) {
                    _step.value = OnboardingStep.LoadFailed(e.message ?: "Could not load playlists")
                } catch (e: HttpException) {
                    // Retrofit throws HttpException (a RuntimeException, NOT an
                    // IOException) for non-2xx responses - a 401/403/429/5xx
                    // right after the PKCE token exchange (very plausible: a
                    // revoked token, a missing scope, or a rate limit from the
                    // exchange burst) would otherwise crash the app uncaught on
                    // the very first screen after login. Every other ViewModel
                    // that hits the network catches both for the same reason.
                    _step.value = OnboardingStep.LoadFailed("Could not load playlists (${e.code()})")
                }
            }
        }

        fun selectPlaylist(playlistId: String) = sync(playlistId)

        fun useLikedSongs() = sync(SpotifyRepository.LIKED_SONGS_SENTINEL)

        fun confirmManualInput(raw: String) {
            extractSpotifyId(raw)?.let { sync(it) }
        }

        fun skip() {
            appPreferences.isOnboarded = true
            _step.value = OnboardingStep.Done
        }

        private fun sync(playlistId: String) {
            _step.value = OnboardingStep.Syncing
            viewModelScope.launch {
                when (val result = tasteProfileRepository.syncPlaylist(playlistId)) {
                    is SyncResult.Success -> {
                        appPreferences.activeProfilePlaylistId = playlistId
                        appPreferences.isOnboarded = true
                        _step.value = OnboardingStep.Done
                    }

                    is SyncResult.Refused -> {
                        _step.value = OnboardingStep.SyncFailed(result.reason)
                    }

                    is SyncResult.Failed -> {
                        _step.value = OnboardingStep.SyncFailed(result.error)
                    }
                }
            }
        }
    }
