package com.ventus.sys.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ventus.sys.ui.common.RequestBatteryExemption

private const val SPOTIFY_DASHBOARD_URL = "https://developer.spotify.com/dashboard"

/**
 * Client-ID entry + Spotify login trigger. Real credential/PKCE work is in
 * AuthViewModel; this is presentation only.
 *
 * [RequestBatteryExemption] is called here — before login, not after
 * onboarding — because the OEM-kill race it mitigates happens during the
 * Custom Tab handoff itself (see that composable's doc comment for why
 * this is the right place for it).
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val context = LocalContext.current
    RequestBatteryExemption()

    // safeDrawingPadding() defensively, same reasoning as OnboardingScreen -
    // this content only avoids the status/nav-bar insets today because it's
    // vertically centered with enough natural margin; an Error message
    // (which pushes content, see below) or a taller device font could close
    // that gap without this.
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "VENTUS // SYS", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Connect your Spotify account to get started", style = MaterialTheme.typography.bodySmall)

        if (uiState is AuthUiState.Error) {
            Text(
                text = "// ${(uiState as AuthUiState.Error).message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        OutlinedTextField(
            value = clientId,
            onValueChange = viewModel::onClientIdChanged,
            label = { Text("Spotify Client ID") },
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Button(onClick = onLoginClick, enabled = uiState !is AuthUiState.LoggingIn) {
            Text(if (uiState is AuthUiState.LoggingIn) "Waiting for Spotify…" else "Log in with Spotify")
        }
        // Low-emphasis on purpose: existing users with a Client ID already
        // saved never need this, and new users shouldn't feel like the
        // Developer Dashboard is a required step just to reach Login.
        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_DASHBOARD_URL)))
            },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Get a Client ID at developer.spotify.com ⟶", style = MaterialTheme.typography.labelSmall)
        }
    }
}
