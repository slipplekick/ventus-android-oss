package com.ventus.sys.data.auth

import net.openid.appauth.AuthorizationServiceConfiguration

/** Spotify's PKCE endpoints and the OAuth scopes this app requests. */
object SpotifyAuthConfig {
    private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
    private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

    // Custom URI scheme redirect via Chrome Custom Tab — simpler than
    // desktop's loopback-port approach, no fixed local port to collide with.
    // Must match app/build.gradle.kts' manifestPlaceholders["appAuthRedirectScheme"]
    // and the intent-filter AppAuth's own manifest merges in.
    const val REDIRECT_URI = "ventus://oauth/callback"

    val SCOPES =
        listOf(
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-top-read",
            "user-read-recently-played",
            "user-library-read",
            "playlist-read-private",
            "playlist-modify-public",
            "playlist-modify-private",
            "user-follow-read",
            "streaming",
        )

    val serviceConfig: AuthorizationServiceConfiguration =
        AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_ENDPOINT),
            android.net.Uri.parse(TOKEN_ENDPOINT),
        )
}
