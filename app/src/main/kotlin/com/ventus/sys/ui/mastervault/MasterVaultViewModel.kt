package com.ventus.sys.ui.mastervault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.local.MasterVaultPlaylistSummary
import com.ventus.sys.data.repository.MasterVaultRepository
import com.ventus.sys.data.repository.MasterVaultStats
import com.ventus.sys.data.repository.MasterVaultTrackUiItem
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.playlistFetchErrorMessage
import com.ventus.sys.domain.extractSpotifyId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
private const val TAG = "MasterVaultViewModel"

sealed interface MasterVaultSyncState {
    data object Idle : MasterVaultSyncState

    data class Syncing(
        val progress: String? = null,
    ) : MasterVaultSyncState

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
        private val spotifyRepository: SpotifyRepository,
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

        /**
         * Full-precision CSV of whatever's currently in view (one playlist,
         * or everything if "ALL PLAYLISTS" is selected) — unlike Session
         * History, this data isn't ephemeral-per-app-launch, so getting it
         * off the device as a real file is worth having, not just browsing
         * in-app.
         */
        suspend fun buildCsv(): String = masterVaultRepository.buildCsv(uiState.value.selectedPlaylistId)

        /** Ports app.js's masterVaultSync (app.js:2706-2731) / app.py's master_vault_sync (app.py:2402-2518). */
        fun sync() {
            val id =
                extractSpotifyId(playlistInput.value) ?: run {
                    syncState.value = MasterVaultSyncState.Error("Enter a playlist ID or URL first")
                    return
                }
            syncState.value = MasterVaultSyncState.Syncing()
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

        /**
         * Imports every playlist on the logged-in account — [SpotifyRepository.getUserPlaylists]
         * (first 50, Spotify's own page-size cap, same limit that screen's own doc comment
         * already documents) fed one at a time into the same [MasterVaultRepository.syncPlaylist]
         * a manual import uses. Sequential, not parallel — each sync already does its own
         * Spotify + ReccoBeats calls, and running 50 of those concurrently would be exactly the
         * kind of burst that trips ReccoBeats' 429 rate limit for no real speed benefit, since
         * the whole operation is bottlenecked on those network calls either way.
         */
        fun importAll() {
            syncState.value = MasterVaultSyncState.Syncing("Loading your playlists…")
            viewModelScope.launch {
                val playlists =
                    try {
                        spotifyRepository.getUserPlaylists()
                    } catch (e: IOException) {
                        syncState.value = MasterVaultSyncState.Error(e.message ?: "Could not load playlists")
                        return@launch
                    } catch (e: HttpException) {
                        syncState.value = MasterVaultSyncState.Error(playlistFetchErrorMessage(e))
                        return@launch
                    }
                var totalAdded = 0
                var totalGhost = 0
                // Names, not just a count - a bare "7 failed" gives the user
                // nothing to act on. Logged too (Log.w), since the exception
                // message itself (rate limit vs. permissions vs. a genuinely
                // malformed playlist) is worth having in logcat even though
                // the UI only has room for which playlists, not why each one.
                val failedPlaylists = mutableListOf<String>()
                playlists.forEachIndexed { index, playlist ->
                    syncState.value =
                        MasterVaultSyncState.Syncing("Importing ${index + 1}/${playlists.size}: ${playlist.name}")
                    try {
                        val result = masterVaultRepository.syncPlaylist(playlist.id)
                        totalAdded += result.added
                        totalGhost += result.ghost
                    } catch (e: IOException) {
                        failedPlaylists += playlist.name
                        Log.w(TAG, "importAll: \"${playlist.name}\" (${playlist.id}) failed", e)
                    } catch (e: CancellationException) {
                        // Must be its own catch, ahead of the broad Exception one below -
                        // a coroutine cancellation (e.g. leaving the screen mid-import)
                        // has to propagate, not get swallowed as "one playlist failed."
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        // Deliberately broad, not just HttpException: a single bad/private/
                        // deleted/malformed playlist (unlikely but possible mid-sweep, or if
                        // Spotify sends a shape this app's DTOs don't expect) shouldn't abort
                        // the other 13 - same "keep going" principle as syncPlaylist's own
                        // callers, just widened after a real sweep hit something HttpException/
                        // IOException alone didn't catch and needed a rebuild to even see why.
                        failedPlaylists += playlist.name
                        Log.w(TAG, "importAll: \"${playlist.name}\" (${playlist.id}) failed", e)
                    }
                }
                val succeeded = playlists.size - failedPlaylists.size
                syncState.value =
                    MasterVaultSyncState.Done(
                        "Imported $succeeded/${playlists.size} playlists — $totalAdded newly resolved, $totalGhost ghost" +
                            if (failedPlaylists.isNotEmpty()) ". Failed: ${failedPlaylists.joinToString(", ")}" else "",
                    )
                selectedPlaylistId.value = null
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
