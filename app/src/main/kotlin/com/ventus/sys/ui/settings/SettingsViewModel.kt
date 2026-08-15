package com.ventus.sys.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.local.AppPreferences
import com.ventus.sys.data.local.PlaylistPresetEntity
import com.ventus.sys.data.remote.dto.UserPlaylistDto
import com.ventus.sys.data.repository.PlaylistPresetRepository
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.domain.extractSpotifyId
import com.ventus.sys.service.AutoAddStateHolder
import com.ventus.sys.service.AutoAddStatus
import com.ventus.sys.service.AutoSyncStateHolder
import com.ventus.sys.service.AutoSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class SettingsUiState(
    val autoSyncEnabled: Boolean = false,
    val playlistInput: String = "",
    val intervalSeconds: Int = AppPreferences.DEFAULT_AUTOSYNC_INTERVAL_SECONDS,
    val status: AutoSyncStatus = AutoSyncStatus(),
    val enableError: String? = null,
    val autoAddEnabled: Boolean = false,
    val autoAddThreshold: Int = AppPreferences.DEFAULT_AUTOADD_THRESHOLD,
    val autoAddPlaylistInput: String = "",
    val autoAddStatus: AutoAddStatus = AutoAddStatus(),
    val autoAddError: String? = null,
    val presets: List<PlaylistPresetEntity> = emptyList(),
    val newPresetName: String = "",
    val newPresetId: String = "",
    // Own-account playlists for the target-playlist dropdowns below - both
    // Auto-Sync and Auto-Add previously only offered a raw "paste an ID or
    // URL" field for a playlist that's always the user's own, which needed
    // them to go find and copy a Spotify share link just to fill in a
    // setting about their own library. Empty (not an error state) if the
    // fetch fails - the manual paste field is still right there as a
    // fallback, same as it always was.
    val myPlaylists: List<UserPlaylistDto> = emptyList(),
)

/**
 * Ports app.js's toggleAutoSync (app.js:839-863) / app.py's set_autosync
 * (app.py:2277-2290), plus Auto-Add's toggle/threshold/playlist-preset UI
 * (maybeAutoAdd app.js:1646-1671, preset manager app.js:1510-1634) — both
 * live in the same persistent sidebar panel on desktop (index.html:117-155),
 * so they share this one Settings screen on Android too.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appPreferences: AppPreferences,
        private val playlistPresetRepository: PlaylistPresetRepository,
        private val spotifyRepository: SpotifyRepository,
        autoSyncStateHolder: AutoSyncStateHolder,
        autoAddStateHolder: AutoAddStateHolder,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    autoSyncEnabled = appPreferences.autoSyncEnabled,
                    playlistInput = appPreferences.autoSyncPlaylistId.orEmpty(),
                    intervalSeconds = appPreferences.autoSyncIntervalSeconds,
                    autoAddEnabled = appPreferences.autoAddEnabled,
                    autoAddThreshold = appPreferences.autoAddThreshold,
                    autoAddPlaylistInput = appPreferences.autoAddPlaylistId.orEmpty(),
                ),
            )
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                autoSyncStateHolder.status.collect { status ->
                    _uiState.value = _uiState.value.copy(status = status)
                }
            }
            viewModelScope.launch {
                autoAddStateHolder.status.collect { status ->
                    _uiState.value = _uiState.value.copy(autoAddStatus = status)
                }
            }
            viewModelScope.launch {
                playlistPresetRepository.observeAll().collect { presets ->
                    _uiState.value = _uiState.value.copy(presets = presets)
                }
            }
            viewModelScope.launch {
                val playlists =
                    try {
                        spotifyRepository.getUserPlaylists()
                    } catch (
                        @Suppress("SwallowedException")
                        e: IOException,
                    ) {
                        // Non-critical - the manual paste field still works if this
                        // fails, so there's nothing worth surfacing as an error here.
                        return@launch
                    } catch (
                        @Suppress("SwallowedException")
                        e: HttpException,
                    ) {
                        return@launch
                    }
                _uiState.value = _uiState.value.copy(myPlaylists = playlists)
            }
        }

        fun selectAutoSyncPlaylist(playlist: UserPlaylistDto) {
            onPlaylistInputChanged(playlist.id)
        }

        fun selectAutoAddPlaylist(playlist: UserPlaylistDto) {
            onAutoAddPlaylistInputChanged(playlist.id)
        }

        fun onPlaylistInputChanged(value: String) {
            _uiState.value = _uiState.value.copy(playlistInput = value, enableError = null)
        }

        fun onIntervalChanged(seconds: Int) {
            appPreferences.autoSyncIntervalSeconds = seconds
            _uiState.value = _uiState.value.copy(intervalSeconds = appPreferences.autoSyncIntervalSeconds)
        }

        /** Matches app.js's own guard (app.js:844-848) — needs a target playlist before it can be enabled. */
        fun setEnabled(enabled: Boolean) {
            if (!enabled) {
                appPreferences.autoSyncEnabled = false
                _uiState.value = _uiState.value.copy(autoSyncEnabled = false, enableError = null)
                return
            }

            val id = extractSpotifyId(_uiState.value.playlistInput)
            if (id == null) {
                _uiState.value = _uiState.value.copy(enableError = "Set a target playlist first")
                return
            }
            appPreferences.autoSyncPlaylistId = id
            appPreferences.autoSyncEnabled = true
            _uiState.value = _uiState.value.copy(autoSyncEnabled = true, enableError = null)
        }

        fun onAutoAddPlaylistInputChanged(value: String) {
            _uiState.value = _uiState.value.copy(autoAddPlaylistInput = value, autoAddError = null)
        }

        fun onAutoAddThresholdChanged(threshold: Int) {
            appPreferences.autoAddThreshold = threshold
            _uiState.value = _uiState.value.copy(autoAddThreshold = appPreferences.autoAddThreshold)
        }

        /** Same "needs a target first" guard as auto-sync (app.js:1651-1654's runtime check, enforced at enable-time here instead). */
        fun setAutoAddEnabled(enabled: Boolean) {
            if (!enabled) {
                appPreferences.autoAddEnabled = false
                _uiState.value = _uiState.value.copy(autoAddEnabled = false, autoAddError = null)
                return
            }
            val id = extractSpotifyId(_uiState.value.autoAddPlaylistInput)
            if (id == null) {
                _uiState.value = _uiState.value.copy(autoAddError = "Set a target playlist first")
                return
            }
            appPreferences.autoAddPlaylistId = id
            appPreferences.autoAddEnabled = true
            _uiState.value = _uiState.value.copy(autoAddEnabled = true, autoAddError = null)
        }

        fun selectPreset(preset: PlaylistPresetEntity) {
            _uiState.value = _uiState.value.copy(autoAddPlaylistInput = preset.playlistId, autoAddError = null)
        }

        fun onNewPresetNameChanged(value: String) {
            _uiState.value = _uiState.value.copy(newPresetName = value)
        }

        fun onNewPresetIdChanged(value: String) {
            _uiState.value = _uiState.value.copy(newPresetId = value)
        }

        /** Ports app.js's addPreset (app.js:1596-1608). */
        fun saveNewPreset() {
            val state = _uiState.value
            val name = state.newPresetName.trim()
            val id = extractSpotifyId(state.newPresetId.trim()) ?: return
            if (name.isEmpty()) return
            viewModelScope.launch {
                playlistPresetRepository.save(id, name)
                _uiState.value = _uiState.value.copy(newPresetName = "", newPresetId = "")
            }
        }

        /** Ports app.js's deletePreset (app.js:1610-1619). */
        fun deletePreset(preset: PlaylistPresetEntity) {
            viewModelScope.launch { playlistPresetRepository.delete(preset) }
        }
    }
