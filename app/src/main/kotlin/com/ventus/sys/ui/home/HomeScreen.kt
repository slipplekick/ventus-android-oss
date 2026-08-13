package com.ventus.sys.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ventus.sys.service.NowPlayingService
import com.ventus.sys.ui.common.OemBackgroundKillOnboarding
import com.ventus.sys.ui.common.RequestBatteryExemption
import com.ventus.sys.ui.navigation.VentusNavHost

/**
 * Post-onboarding shell — hosts [VentusNavHost] and its bottom nav.
 *
 * Starts [NowPlayingService] here (not MainActivity's onCreate) so it only
 * starts once the user has actually reached a screen that needs it —
 * matches the "start once authenticated and onboarded" behavior desktop's
 * own poller has (app.py's poller starts as soon as Flask boots with valid
 * credentials, which by definition means setup already completed).
 *
 * POST_NOTIFICATIONS (Android 13+) is requested here since a Service can't
 * request runtime permissions itself. [RequestBatteryExemption] is called
 * here too as a second chance — LoginScreen is the primary place it's
 * requested (before the OAuth handshake, the actual risky window), but
 * calling it again here is a harmless no-op if already granted there, and
 * gives a second chance if the user declined it initially.
 *
 * [OemBackgroundKillOnboarding] runs alongside it — a separate, OEM-specific
 * concern [RequestBatteryExemption]'s stock-Android dialog can't cover.
 * Shown here (not LoginScreen) since it's a one-time "go change a setting"
 * ask, not part of the login critical path.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val notificationPermission = rememberNotificationPermissionLauncher()
    RequestBatteryExemption()
    OemBackgroundKillOnboarding()

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        NowPlayingService.start(context)
    }

    VentusNavHost()
}

@Composable
private fun rememberNotificationPermissionLauncher() =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // No-op: the foreground service still runs without this grant — a missing
        // POST_NOTIFICATIONS permission on 13+ suppresses the notification, not the
        // service itself, so there's nothing else to react to here.
    }
