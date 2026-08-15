package com.ventus.sys.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventus.sys.data.auth.PkceAuthManager
import com.ventus.sys.data.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import javax.inject.Inject

// The ActivityResult callback can still land successfully a moment after a
// process restart (Android's own state-restore survives a plain process
// kill, just not some OEMs' more destructive one) - so checking the
// pending-login flag immediately on init would produce a false-positive
// error flash on runs that were about to succeed anyway. This grace period
// gives that callback a chance to arrive and clear the flag itself first.
private const val LOGIN_RECOVERY_GRACE_PERIOD_MS = 4000L

sealed interface AuthUiState {
    data object LoggedOut : AuthUiState

    data object LoggingIn : AuthUiState

    data class LoggedIn(
        val hasValidToken: Boolean,
    ) : AuthUiState

    data class Error(
        val message: String,
    ) : AuthUiState
}

/**
 * Drives PKCE login end-to-end: client-ID entry, launching the AppAuth
 * authorization intent, and handling its result. TokenStore/PkceAuthManager
 * underneath do the actual credential work; this ViewModel is presentation
 * state on top of them.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authManager: PkceAuthManager,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        private var authState: AuthState? = null

        private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.LoggedOut)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        private val _clientId = MutableStateFlow(tokenStore.loadClientId().orEmpty())
        val clientId: StateFlow<String> = _clientId.asStateFlow()

        init {
            tokenStore.loadAuthState()?.let { restored ->
                if (restored.isAuthorized) {
                    authState = restored
                    _uiState.value = AuthUiState.LoggedIn(hasValidToken = true)
                }
            }
            // See TokenStore.markLoginAttemptStarted's doc comment: if this fires,
            // the OS killed the app mid-handshake last time, not the user cancelling.
            // Only worth surfacing if that attempt didn't already succeed above -
            // and only after giving handleAuthResponseIntent's own callback a grace
            // period to arrive and clear the flag itself (see the constant's comment).
            if (_uiState.value !is AuthUiState.LoggedIn && tokenStore.hasPendingLoginAttempt()) {
                viewModelScope.launch {
                    delay(LOGIN_RECOVERY_GRACE_PERIOD_MS)
                    if (_uiState.value !is AuthUiState.LoggedIn && tokenStore.consumePendingLoginAttempt()) {
                        _uiState.value =
                            AuthUiState.Error(
                                "Login didn't complete - your phone likely closed VENTUS in the background " +
                                    "during the handshake. Tap Log in with Spotify to try again.",
                            )
                    }
                }
            }
        }

        fun onClientIdChanged(value: String) {
            _clientId.value = value
        }

        /** Builds the AppAuth login intent for MainActivity to launch. Null if no client ID entered yet. */
        fun buildLoginIntent(): Intent? {
            val id = _clientId.value.trim()
            if (id.isEmpty()) {
                _uiState.value = AuthUiState.Error("Enter a Spotify Client ID first")
                return null
            }
            tokenStore.saveClientId(id)
            tokenStore.markLoginAttemptStarted()
            _uiState.value = AuthUiState.LoggingIn
            val request = authManager.buildAuthorizationRequest(id)
            return authManager.getAuthorizationRequestIntent(request)
        }

        /** Called with the Intent returned by the AppAuth login activity result. */
        fun handleAuthResponseIntent(intent: Intent) {
            // A real callback reaching us at all (success, user-cancel, or a genuine
            // AppAuth error) means state wasn't lost - only the no-callback-at-all
            // case (process killed, checked in init{}) is what that flag is for.
            tokenStore.consumePendingLoginAttempt()
            val (response, exception) = authManager.parseAuthorizationResponse(intent)
            if (exception != null || response == null) {
                _uiState.value = AuthUiState.Error(exception?.errorDescription ?: "Login cancelled or failed")
                return
            }
            viewModelScope.launch {
                try {
                    val newState = authManager.exchangeAuthorizationCode(response)
                    authState = newState
                    tokenStore.saveAuthState(newState)
                    _uiState.value = AuthUiState.LoggedIn(hasValidToken = true)
                } catch (e: AuthorizationException) {
                    // exchangeAuthorizationCode only ever fails via AuthorizationException
                    // (see PkceAuthManager — resumeWithException always passes one).
                    _uiState.value = AuthUiState.Error("Token exchange failed: ${e.errorDescription ?: e.message}")
                }
            }
        }

        fun logout() {
            authState = null
            tokenStore.clearAuthState()
            _uiState.value = AuthUiState.LoggedOut
        }
    }
