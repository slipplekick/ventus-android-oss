package com.ventus.sys.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore-backed credential storage — encrypted at rest, since a
 * phone is far more loseable than a desktop machine.
 *
 * Stores the serialized [AuthState] blob, not individual tokens: AppAuth's
 * own AuthState.update() already implements the "only overwrite the refresh
 * token when the response actually includes a new one" rule correctly —
 * hand-rolling that here would just be re-implementing something AppAuth
 * already gets right. This class only owns persistence, not the update
 * logic itself.
 */
@Singleton
class TokenStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        private val prefs =
            EncryptedSharedPreferences.create(
                context,
                "ventus_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        fun saveAuthState(authState: AuthState) {
            prefs.edit().putString(KEY_AUTH_STATE, authState.jsonSerializeString()).apply()
        }

        fun loadAuthState(): AuthState? {
            val json = prefs.getString(KEY_AUTH_STATE, null) ?: return null
            return runCatching { AuthState.jsonDeserialize(json) }.getOrNull()
        }

        fun clearAuthState() {
            prefs.edit().remove(KEY_AUTH_STATE).apply()
        }

        // The Spotify Client ID isn't secret — that's the whole point of PKCE
        // for public clients — but it's still stored alongside the tokens for
        // convenience: one place, one file, and it never needs re-entering
        // across app restarts.
        fun saveClientId(clientId: String) {
            prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
        }

        fun loadClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

        /**
         * Marks that a login attempt is about to hand off to the Custom Tab.
         * Written to disk (not just ViewModel state) because some OEM builds
         * can fully kill the app process while it's backgrounded behind
         * Chrome for the OAuth handshake — fast enough that it isn't Doze
         * (which needs idle+screen-off), and severe enough that AppAuth's own
         * AuthorizationManagementActivity loses its pending-request state
         * ("No stored state - unable to handle response") and never forwards
         * a result at all. When that happens the whole process restarts with
         * a brand-new AuthViewModel that has no memory of the attempt — only
         * a flag durable across that restart lets init{} recognize what
         * happened and surface it, instead of silently reverting to a blank
         * login screen as if nothing was tried.
         */
        fun markLoginAttemptStarted() {
            prefs.edit().putBoolean(KEY_LOGIN_ATTEMPT_PENDING, true).apply()
        }

        /** Non-destructive peek — lets AuthViewModel decide to wait before treating a pending attempt as truly lost. */
        fun hasPendingLoginAttempt(): Boolean = prefs.getBoolean(KEY_LOGIN_ATTEMPT_PENDING, false)

        /** True if a login attempt was started but never reached [handleAuthResponseIntent] — consumes the flag either way. */
        fun consumePendingLoginAttempt(): Boolean {
            val wasPending = prefs.getBoolean(KEY_LOGIN_ATTEMPT_PENDING, false)
            if (wasPending) prefs.edit().putBoolean(KEY_LOGIN_ATTEMPT_PENDING, false).apply()
            return wasPending
        }

        private companion object {
            const val KEY_AUTH_STATE = "auth_state"
            const val KEY_CLIENT_ID = "spotify_client_id"
            const val KEY_LOGIN_ATTEMPT_PENDING = "login_attempt_pending"
        }
    }
