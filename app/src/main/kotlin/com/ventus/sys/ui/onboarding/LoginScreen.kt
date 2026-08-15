package com.ventus.sys.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ventus.sys.data.auth.SpotifyAuthConfig
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
    // Expanded by default only for a genuinely first-time user (no Client ID
    // saved yet) — this is the ONE Spotify Dashboard step every new install
    // needs before login can work at all, so it has to be visible up front,
    // not tucked behind a link only someone who already knows what a
    // "redirect URI" is would think to click. Once collapsed (or once a
    // Client ID is saved on a later launch), it stays out of the way for
    // people who've already done this. `remember` with no key captures only
    // the startup value — later edits to clientId don't fight the user's
    // own manual toggle.
    var setupExpanded by remember { mutableStateOf(clientId.isBlank()) }
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
        TextButton(onClick = { setupExpanded = !setupExpanded }) {
            Text(
                if (setupExpanded) "Hide setup steps" else "Need a Client ID? Setup steps ⟶",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (setupExpanded) {
            SpotifySetupSteps(
                onOpenDashboard = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_DASHBOARD_URL)))
                },
            )
        }
    }
}

/**
 * Inline, always-visible-by-default (for a first-time user) walkthrough of
 * exactly what the Spotify Dashboard needs: which API to enable, and the
 * app's redirect URI (copy-to-clipboard, since a typo or missing detail
 * here is what produces Spotify's own "redirect URI: not matching
 * configuration" error — a server-side rejection that happens before
 * VENTUS even sees a response, so there's nothing the app itself can
 * validate or work around). Deliberately inline rather than behind a
 * dialog/link someone would only click if they already knew this step
 * existed — every new install needs it, so it has to be part of the main
 * screen, not troubleshooting content.
 */
@Composable
private fun SpotifySetupSteps(onOpenDashboard: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(16.dp),
    ) {
        Text(
            "1. Create an app at the Spotify Developer Dashboard — any name/description is fine.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onOpenDashboard) {
            Text("Open developer.spotify.com/dashboard ⟶", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "2. When asked which APIs/SDKs you're using, check \"Web API\" — that's the only one VENTUS needs.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("3. Under Redirect URIs, add exactly this (case-sensitive, no trailing slash):", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = SpotifyAuthConfig.REDIRECT_URI,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(SpotifyAuthConfig.REDIRECT_URI))
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy redirect URI")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "4. Save, then copy the Client ID from the app's Settings page into the field above. " +
                "No client secret is needed — VENTUS uses PKCE, which doesn't require one.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
