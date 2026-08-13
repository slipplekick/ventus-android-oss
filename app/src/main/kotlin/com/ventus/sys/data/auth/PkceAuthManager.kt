package com.ventus.sys.data.auth

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around AppAuth-Android's [AuthorizationService] — Authorization
 * Code + PKCE flow (Spotify's recommended approach for native/public clients;
 * PKCE avoids needing an embedded client secret at all). Don't hand-roll PKCE:
 * AuthorizationRequest.Builder generates and manages the code verifier/
 * challenge automatically.
 */
@Singleton
class PkceAuthManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // AppAuthConfiguration defaults to preferring a Custom Tab (via the
        // androidx.browser dependency pinned in app/build.gradle.kts) over a
        // raw WebView, which is what this project uses throughout.
        private val authService = AuthorizationService(context)

        fun buildAuthorizationRequest(clientId: String): AuthorizationRequest =
            AuthorizationRequest
                .Builder(
                    SpotifyAuthConfig.serviceConfig,
                    clientId,
                    ResponseTypeValues.CODE,
                    android.net.Uri.parse(SpotifyAuthConfig.REDIRECT_URI),
                ).setScopes(SpotifyAuthConfig.SCOPES)
                .build()

        fun getAuthorizationRequestIntent(request: AuthorizationRequest): Intent = authService.getAuthorizationRequestIntent(request)

        /**
         * Exchanges an authorization code for tokens. `clientId` is passed
         * explicitly in the token request body — Spotify's PKCE token
         * exchange/refresh requests need it even though there's no
         * client_secret (public client).
         */
        suspend fun exchangeAuthorizationCode(response: AuthorizationResponse): AuthState =
            suspendCancellableCoroutine { cont ->
                val authState = AuthState(response, null)
                authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                    authState.update(tokenResponse, ex)
                    if (tokenResponse != null) {
                        cont.resume(authState)
                    } else {
                        cont.resumeWithException(ex ?: AuthorizationException.GeneralErrors.NETWORK_ERROR)
                    }
                }
            }

        /**
         * Returns a fresh access token, refreshing via AppAuth's own
         * performActionWithFreshTokens if the cached one has expired. AuthState
         * mutates in place (new tokens get written back into it), so the caller
         * must re-persist it via TokenStore after this returns.
         */
        suspend fun getFreshAccessToken(authState: AuthState): String =
            suspendCancellableCoroutine { cont ->
                authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                    if (accessToken != null) {
                        cont.resume(accessToken)
                    } else {
                        cont.resumeWithException(ex ?: AuthorizationException.GeneralErrors.NETWORK_ERROR)
                    }
                }
            }

        fun parseAuthorizationResponse(intent: Intent): Pair<AuthorizationResponse?, AuthorizationException?> =
            AuthorizationResponse.fromIntent(intent) to AuthorizationException.fromIntent(intent)

        // Deliberately no dispose()/AuthorizationService.dispose() here: this
        // class is @Singleton (application-scoped via Hilt). Disposing it from
        // a shorter-lived owner (e.g. an Activity's onCleared()) would break
        // every subsequent auth call for the rest of the process's life —
        // once disposed, AppAuth's AuthorizationService throws
        // IllegalStateException on any further use, and NowPlayingService (a
        // foreground service) can easily outlive the Activity.
    }
