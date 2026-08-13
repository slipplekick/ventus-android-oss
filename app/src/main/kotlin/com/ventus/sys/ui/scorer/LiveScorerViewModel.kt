package com.ventus.sys.ui.scorer

import androidx.lifecycle.ViewModel
import com.ventus.sys.service.NowPlayingState
import com.ventus.sys.service.NowPlayingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin pass-through — the real state lives in the singleton [NowPlayingStateHolder] the service writes to. */
@HiltViewModel
class LiveScorerViewModel
    @Inject
    constructor(
        stateHolder: NowPlayingStateHolder,
    ) : ViewModel() {
        val state: StateFlow<NowPlayingState> = stateHolder.state
    }
