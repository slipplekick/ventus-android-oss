package com.ventus.sys.ui.mastervault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.local.MasterVaultPlaylistSummary
import com.ventus.sys.data.repository.MasterVaultRepository
import com.ventus.sys.data.repository.MasterVaultStats
import com.ventus.sys.data.repository.MasterVaultTrackUiItem
import com.ventus.sys.data.repository.playlistFetchErrorMessage
import com.ventus.sys.domain.extractSpotifyId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

sealed interface MasterVaultSyncState {
    data object Idle : MasterVaultSyncState

    data object Syncing : MasterVaultSyncState

    data class Done(
        val message: String,
    ) : MasterVaultSyncState

    data class Error(
        val message: String,
    ) : MasterVaultSyncState
}

data class MasterVaultUiState(
    val playlists: List<MasterVaultPlaylistSummary> = emptyList(),
    val selectedPlaylistId: String? = null,
    val stats: MasterVaultStats = MasterVaultStats(0, 0, 0),
    val tracks: List<MasterVaultTrackUiItem> = emptyList(),
    val syncState: MasterVaultSyncState = MasterVaultSyncState.Idle,
)

/**
 * Ports app.js's Master Vault overlay (openMasterVault + friends, app.js:
 * 2594-2764) / app.py's master_vault_* routes (app.py:2345-2550). No CSV
 * export here (unlike Session History) — app.py's version writes silently
 * to a `playlist_vaults/` folder next to the server process, which has no
 * meaningful Android equivalent (there's no "next to the app" folder a user
 * would ever browse to); Session History's SAF export pattern already
 * covers the "get my data out" need this app actually has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MasterVaultViewModel
    @Inject
    constructor(
        private val masterVaultRepository: MasterVaultRepository,
    ) : ViewModel() {
        val playlistInput = MutableStateFlow("")
        private val selectedPlaylistId = MutableStateFlow<String?>(null)
        private val syncState = MutableStateFlow<MasterVaultSyncState>(MasterVaultSyncState.Idle)

        val uiState: StateFlow<MasterVaultUiState> =
            combine(
                masterVaultRepository.observePlaylists(),
                masterVaultRepository.observeStats(),
                selectedPlaylistId,
                selectedPlaylistId.flatMapLatest { masterVaultRepository.observeTracks(it) },
                syncState,
            ) { playlists, stats, selected, tracks, sync ->
                MasterVaultUiState(
                    playlists = playlists,
                    selectedPlaylistId = selected,
                    stats = stats,
                    tracks = tracks,
                    syncState = sync,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MasterVaultUiState())

        fun selectPlaylist(id: String?) {
            selectedPlaylistId.value = id
        }

        /** Ports app.js's masterVaultSync (app.js:2706-2731) / app.py's master_vault_sync (app.py:2402-2518). */
        fun sync() {
            val id =
                extractSpotifyId(playlistInput.value) ?: run {
                    syncState.value = MasterVaultSyncState.Error("Enter a playlist ID or URL first")
                    return
                }
            syncState.value = MasterVaultSyncState.Syncing
            viewModelScope.launch {
                val result =
                    try {
                        masterVaultRepository.syncPlaylist(id)
                    } catch (e: IOException) {
                        syncState.value = MasterVaultSyncState.Error(e.message ?: "Import failed")
                        return@launch
                    } catch (e: HttpException) {
                        syncState.value = MasterVaultSyncState.Error(playlistFetchErrorMessage(e))
                        return@launch
                    }
                syncState.value =
                    MasterVaultSyncState.Done(
                        "\"${result.playlistName}\": ${result.added} indexed, ${result.ghost} ghost, ${result.already} already cached.",
                    )
                // Jump straight to what was just imported, same as app.js:2724.
                selectedPlaylistId.value = id
                playlistInput.value = ""
            }
        }

        /** Ports app.js's masterVaultClear (app.js:2747-2764) / app.py's master_vault_clear (app.py:2520-2540). */
        fun clearSelected() {
            val target = uiState.value.selectedPlaylistId
            viewModelScope.launch {
                masterVaultRepository.clear(target)
                selectedPlaylistId.value = null
            }
        }
    }
