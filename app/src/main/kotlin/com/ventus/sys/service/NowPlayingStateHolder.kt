package com.ventus.sys.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton so it survives independently of the Service's own lifecycle
 * (a Service can be destroyed/recreated by the system; ViewModels reading
 * this need a stable source that doesn't get wiped when that happens) and
 * is injectable into both [NowPlayingService] (the writer) and any
 * ViewModel (Live Scorer, the reader) without either needing a reference
 * to the other.
 */
@Singleton
class NowPlayingStateHolder
    @Inject
    constructor() {
        private val _state = MutableStateFlow(NowPlayingState())
        val state: StateFlow<NowPlayingState> = _state.asStateFlow()

        fun update(transform: (NowPlayingState) -> NowPlayingState) {
            _state.value = transform(_state.value)
        }
    }
