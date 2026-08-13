package com.ventus.sys.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret app state — plain SharedPreferences, not [com.ventus.sys.data.auth.TokenStore]'s
 * encrypted store (that's for credentials only). Mirrors app.js's `_store`
 * (localStorage) usage for `ventus_onboarded` (app.js:2309) and the active
 * profile playlist ID.
 */
@Singleton
class AppPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("ventus_prefs", Context.MODE_PRIVATE)

        var isOnboarded: Boolean
            get() = prefs.getBoolean(KEY_ONBOARDED, false)
            set(value) = prefs.edit { putBoolean(KEY_ONBOARDED, value) }

        var activeProfilePlaylistId: String?
            get() = prefs.getString(KEY_PROFILE_PLAYLIST_ID, null)
            set(value) = prefs.edit { putString(KEY_PROFILE_PLAYLIST_ID, value) }

        // Ports app.py's _autosync_playlist_id/_autosync_interval globals
        // (app.py:1110-1111) - persisted here instead, since Android has no
        // long-lived process to hold a plain global variable in between app
        // launches the way desktop's daemon thread does.
        var autoSyncEnabled: Boolean
            get() = prefs.getBoolean(KEY_AUTOSYNC_ENABLED, false)
            set(value) = prefs.edit { putBoolean(KEY_AUTOSYNC_ENABLED, value) }

        var autoSyncPlaylistId: String?
            get() = prefs.getString(KEY_AUTOSYNC_PLAYLIST_ID, null)
            set(value) = prefs.edit { putString(KEY_AUTOSYNC_PLAYLIST_ID, value) }

        var autoSyncIntervalSeconds: Int
            get() = prefs.getInt(KEY_AUTOSYNC_INTERVAL, DEFAULT_AUTOSYNC_INTERVAL_SECONDS)
            set(value) = prefs.edit { putInt(KEY_AUTOSYNC_INTERVAL, value.coerceAtLeast(MIN_AUTOSYNC_INTERVAL_SECONDS)) }

        // Ports app.js's autoAddToggle/autoAddThresh/autoAddPlaylistId
        // (app.js:1646-1671, persisted via saveAutoAddState app.js:2267-2273).
        // No default playlist ID is shipped — app.js's own default
        // (`autoAddPlaylistId` pre-filled with the desktop author's personal
        // playlist, index.html:143) is specific to their account, not
        // something to carry into a general-purpose build.
        var autoAddEnabled: Boolean
            get() = prefs.getBoolean(KEY_AUTOADD_ENABLED, false)
            set(value) = prefs.edit { putBoolean(KEY_AUTOADD_ENABLED, value) }

        var autoAddThreshold: Int
            get() = prefs.getInt(KEY_AUTOADD_THRESHOLD, DEFAULT_AUTOADD_THRESHOLD)
            set(value) = prefs.edit { putInt(KEY_AUTOADD_THRESHOLD, value.coerceIn(MIN_AUTOADD_THRESHOLD, MAX_AUTOADD_THRESHOLD)) }

        var autoAddPlaylistId: String?
            get() = prefs.getString(KEY_AUTOADD_PLAYLIST_ID, null)
            set(value) = prefs.edit { putString(KEY_AUTOADD_PLAYLIST_ID, value) }

        // Tracks whether OemBackgroundKillOnboarding's one-time dialog has
        // already been shown/dismissed - shown at most once
        // per install, not once per app launch.
        var oemBackgroundKillPromptShown: Boolean
            get() = prefs.getBoolean(KEY_OEM_PROMPT_SHOWN, false)
            set(value) = prefs.edit { putBoolean(KEY_OEM_PROMPT_SHOWN, value) }

        companion object {
            const val MIN_AUTOSYNC_INTERVAL_SECONDS = 60 // app.py:2284's `max(60, interval)`.
            const val DEFAULT_AUTOSYNC_INTERVAL_SECONDS = 300 // app.py:1111's default.

            // index.html:127's slider bounds and app.js:1649's parseInt fallback.
            const val MIN_AUTOADD_THRESHOLD = 58
            const val MAX_AUTOADD_THRESHOLD = 90
            const val DEFAULT_AUTOADD_THRESHOLD = 75

            private const val KEY_ONBOARDED = "onboarded"
            private const val KEY_PROFILE_PLAYLIST_ID = "active_profile_playlist_id"
            private const val KEY_AUTOSYNC_ENABLED = "autosync_enabled"
            private const val KEY_AUTOSYNC_PLAYLIST_ID = "autosync_playlist_id"
            private const val KEY_AUTOSYNC_INTERVAL = "autosync_interval_seconds"
            private const val KEY_AUTOADD_ENABLED = "autoadd_enabled"
            private const val KEY_AUTOADD_THRESHOLD = "autoadd_threshold"
            private const val KEY_AUTOADD_PLAYLIST_ID = "autoadd_playlist_id"
            private const val KEY_OEM_PROMPT_SHOWN = "oem_background_kill_prompt_shown"
        }
    }
