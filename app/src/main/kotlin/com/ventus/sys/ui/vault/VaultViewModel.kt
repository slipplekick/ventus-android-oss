package com.ventus.sys.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.repository.TasteProfileRepository
import com.ventus.sys.data.repository.VaultRepository
import com.ventus.sys.domain.Camelot
import com.ventus.sys.domain.ScoreEngine
import com.ventus.sys.domain.model.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ID_PREVIEW_LENGTH = 10
private const val STOP_TIMEOUT_MS = 5000L

enum class VaultSort(
    val label: String,
) {
    DEFAULT("ORDER: DEFAULT"),
    SCORE_DESC("SCORE: HIGH → LOW"),
    SCORE_ASC("SCORE: LOW → HIGH"),
    ENERGY_DESC("ENERGY: HIGH → LOW"),
    VALENCE_DESC("VALENCE: HIGH → LOW"),
    BPM_DESC("BPM: HIGH → LOW"),
}

data class VaultUiItem(
    val id: String,
    val song: String,
    val artist: String,
    val isVaultOnly: Boolean,
    val camelot: String,
    val energy: Int,
    val valence: Int,
    val dance: Int,
    val bpm: Int,
    val score: Int,
    val verdict: Verdict,
)

/** Ports app.js's Track Vault screen (renderVault, app.js:509-621) — search/sort over the raw vault feature cache. */
@HiltViewModel
class VaultViewModel
    @Inject
    constructor(
        vaultRepository: VaultRepository,
        tasteProfileRepository: TasteProfileRepository,
    ) : ViewModel() {
        val searchQuery = MutableStateFlow("")
        val sortOption = MutableStateFlow(VaultSort.DEFAULT)

        private val scoredItems: StateFlow<List<VaultUiItem>> =
            combine(
                vaultRepository.observeAll(),
                tasteProfileRepository.observeTrackNames(),
                vaultRepository.observeResolvedNames(),
                tasteProfileRepository.observeProfile(),
            ) { features, playlistNames, resolvedNames, profile ->
                features.map { feature ->
                    // playlistNames (from the synced taste-profile playlist) wins
                    // over resolvedNames (lazily fetched, see VaultRepository) -
                    // no reason to prefer a cached lookup over the authoritative
                    // source when both happen to have an entry.
                    val name = playlistNames[feature.id] ?: resolvedNames[feature.id]
                    if (name == null) {
                        viewModelScope.launch { vaultRepository.resolveNameIfMissing(feature.id) }
                    }
                    val result = ScoreEngine.score(feature, profile)
                    VaultUiItem(
                        id = feature.id,
                        song = name?.song ?: (feature.id.take(ID_PREVIEW_LENGTH) + "…"),
                        artist = name?.artist ?: "VAULT ONLY",
                        isVaultOnly = name == null,
                        camelot = Camelot.get(feature.key, feature.mode),
                        energy = feature.energy.toInt(),
                        valence = feature.valence.toInt(),
                        dance = feature.danceability.toInt(),
                        bpm = feature.bpm.toInt(),
                        score = result.score,
                        verdict = result.verdict,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        val uiState: StateFlow<List<VaultUiItem>> =
            combine(
                scoredItems,
                searchQuery,
                sortOption,
            ) { items, query, sort ->
                items
                    .filter { matchesQuery(it, query) }
                    .let { applySort(it, sort) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private fun matchesQuery(
            item: VaultUiItem,
            query: String,
        ): Boolean {
            if (query.isBlank()) return true
            val q = query.trim()
            return item.song.contains(q, ignoreCase = true) || item.artist.contains(q, ignoreCase = true)
        }

        private fun applySort(
            items: List<VaultUiItem>,
            sort: VaultSort,
        ): List<VaultUiItem> =
            when (sort) {
                VaultSort.DEFAULT -> items
                VaultSort.SCORE_DESC -> items.sortedByDescending { it.score }
                VaultSort.SCORE_ASC -> items.sortedBy { it.score }
                VaultSort.ENERGY_DESC -> items.sortedByDescending { it.energy }
                VaultSort.VALENCE_DESC -> items.sortedByDescending { it.valence }
                VaultSort.BPM_DESC -> items.sortedByDescending { it.bpm }
            }
    }
