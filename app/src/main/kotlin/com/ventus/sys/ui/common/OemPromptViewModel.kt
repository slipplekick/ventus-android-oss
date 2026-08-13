package com.ventus.sys.ui.common

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ventus.sys.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin Hilt-injectable wrapper — Composables can fetch a ViewModel via [hiltViewModel] but not an arbitrary singleton directly. */
@HiltViewModel
class OemPromptViewModel
    @Inject
    constructor(
        val appPreferences: AppPreferences,
    ) : ViewModel()
