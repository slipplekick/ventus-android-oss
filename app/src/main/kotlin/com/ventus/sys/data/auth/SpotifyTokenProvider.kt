package com.ventus.sys.data.auth

import net.openid.appauth.AuthorizationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracted out of SpotifyRepository so every repository that talks to
 * Spotify's Web API (SpotifyRepository, PlaybackRepository, and any other
 * that's added later — search, top tracks, queue, add-to-playlist) shares
 * one fresh-token implementation instead of each hand-rolling its own copy of
 * "load AuthState, refresh via AppAuth if expired, re-persist the mutated
 * state" (PkceAuthManager's own doc comment on why refresh isn't hand-rolled
 * applies here too — don't re-implement something AppAuth already does).
 */
@Singleton
class SpotifyTokenProvider
    @Inject
    constructor(
        private val authManager: PkceAuthManager,
        private val tokenStore: TokenStore,
    ) {
        suspend fun freshAuthHeader(): String {
            val authState =
                tokenStore.loadAuthState()
                    ?: throw AuthorizationException.GeneralErrors.NETWORK_ERROR
            val token = authManager.getFreshAccessToken(authState)
            tokenStore.saveAuthState(authState) // mutated in place on refresh
            return "Bearer $token"
        }
    }
