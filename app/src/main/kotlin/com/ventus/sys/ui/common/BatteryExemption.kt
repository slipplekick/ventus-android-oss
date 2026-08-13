package com.ventus.sys.ui.common

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

/**
 * Requests exemption from the OS's battery-optimization killer,
 * unconditionally on every device (not OEM-detected) — some OEM skins
 * (vivo among them) kill the app within seconds of losing focus to Chrome
 * during the OAuth handshake, well before stock Android's own Doze policy
 * would ever engage, and there's no reliable signal to detect in advance
 * which devices need this.
 *
 * Called from [com.ventus.sys.ui.onboarding.LoginScreen] specifically because
 * that's *before* the risky window (the Custom Tab handoff) opens — asking
 * only in HomeScreen (post-onboarding) is too late for that race. HomeScreen
 * still calls this too, as a harmless no-op if already granted, in case the
 * user declined it here.
 *
 * No-ops entirely if already exempt, so this is safe to call from multiple
 * screens without double-prompting.
 */
@Composable
fun RequestBatteryExemption() {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // No-op: declining just leaves the app exposed to the OEM-kill risk
            // described above, same as before this prompt existed.
        }

    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService<PowerManager>()
        val alreadyExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        if (!alreadyExempt) {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            launcher.launch(intent)
        }
    }
}
