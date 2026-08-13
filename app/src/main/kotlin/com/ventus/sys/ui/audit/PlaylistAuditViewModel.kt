package com.ventus.sys.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.SpotifyRepository
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.data.repository.VaultRepository
import com.ventus.sys.data.repository.playlistFetchErrorMessage
import com.ventus.sys.domain.Camelot
import com.ventus.sys.domain.ScoreEngine
import com.ventus.sys.domain.extractSpotifyId
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

private const val ALIGNED_PLUS_THRESHOLD = 65
private const val PERCENT_MULTIPLIER = 100

data class AuditTrack(
    val id: String,
    val name: String,
    val artist: String,
    val albumArtUrl: String?,
    val camelot: String,
    val energy: Int,
    val valence: Int,
    val score: Int?,
    val verdict: Verdict?,
) {
    /** True when this track was never resolved — app.js's "GHOST" case (audit_playlist's score:null branch, app.py:1524-1526). */
    val isGhost: Boolean get() = score == null
}

data class AuditSummary(
    val totalTracks: Int,
    val avgScore: Int,
    val alignedPlusCount: Int,
    val coreCount: Int,
    val ghostCount: Int,
    val coveragePercent: Int,
)

sealed interface AuditUiState {
    data object Idle : AuditUiState

    data object Auditing : AuditUiState

    data class Error(
        val message: String,
    ) : AuditUiState

    data class Complete(
        val summary: AuditSummary,
        val tracks: List<AuditTrack>,
    ) : AuditUiState
}

/**
 * Ports app.js's runPlaylistAudit (app.js:1761-1841) / app.py's
 * audit_playlist (app.py:1475-1526): score every track in an arbitrary
 * playlist against the CURRENT taste profile, without adding it to that
 * profile (unlike onboarding sync / auto-sync — this is read-only against
 * the playlist itself). No Ghost Signal deep-scan fallback (desktop-only,
 * per FeatureResolver's doc comment) - a track ReccoBeats can't resolve
 * shows as a "GHOST" row (score/verdict null) exactly like app.py's own
 * fallback branch when Ghost Signal also comes up empty, not a crash or a
 * silently-dropped row.
 */
@HiltViewModel
class PlaylistAuditViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyRepository,
        private val vaultRepository: VaultRepository,
        private val tasteProfileRepository: TasteProfileRepository,
    ) : ViewModel() {
        val playlistInput = MutableStateFlow("")

        private val _uiState = MutableStateFlow<AuditUiState>(AuditUiState.Idle)
        val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

        fun runAudit() {
            val id =
                extractSpotifyId(playlistInput.value) ?: run {
                    _uiState.value = AuditUiState.Error("Enter a playlist ID or URL first")
                    return
                }
            _uiState.value = AuditUiState.Auditing
            viewModelScope.launch {
                val remoteTracks =
                    try {
                        spotifyRepository.getPlaylistTracks(id)
                    } catch (e: IOException) {
                        _uiState.value = AuditUiState.Error(e.message ?: "Spotify fetch failed")
                        return@launch
                    } catch (e: HttpException) {
                        _uiState.value = AuditUiState.Error(playlistFetchErrorMessage(e))
                        return@launch
                    }

                val ids = remoteTracks.mapNotNull { it.id }
                val features = vaultRepository.resolveMany(ids)
                val profile = tasteProfileRepository.observeProfile().first()

                val tracks =
                    remoteTracks
                        .mapNotNull { track ->
                            val trackId = track.id ?: return@mapNotNull null
                            val feature = features[trackId]
                            val result = feature?.let { ScoreEngine.score(it, profile) }
                            AuditTrack(
                                id = trackId,
                                name = track.name,
                                artist = track.artistName,
                                albumArtUrl = track.albumArtUrl,
                                camelot = feature?.let { Camelot.get(it.key, it.mode) } ?: "--",
                                energy = feature?.energy?.toInt() ?: 0,
                                valence = feature?.valence?.toInt() ?: 0,
                                score = result?.score,
                                verdict = result?.verdict,
                            )
                        }.sortedByDescending { it.score ?: -1 }

                _uiState.value = AuditUiState.Complete(summarize(tracks), tracks)
            }
        }

        private fun summarize(tracks: List<AuditTrack>): AuditSummary {
            val scored = tracks.filter { !it.isGhost }
            return AuditSummary(
                totalTracks = tracks.size,
                avgScore = if (scored.isNotEmpty()) scored.sumOf { it.score!! } / scored.size else 0,
                alignedPlusCount = scored.count { it.score!! >= ALIGNED_PLUS_THRESHOLD },
                coreCount = scored.count { it.verdict == Verdict.CORE },
                ghostCount = tracks.size - scored.size,
                coveragePercent = if (tracks.isNotEmpty()) scored.size * PERCENT_MULTIPLIER / tracks.size else 0,
            )
        }
    }
