package com.ventus.sys.ui.common

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * One-time "go enable this setting" prompt for OEM background-kill
 * behavior — vivo, Xiaomi/MIUI, Huawei/Honor, and Oppo/Realme/OnePlus are
 * all well-documented offenders here, killing the app within seconds of
 * losing focus, well before stock Android's Doze policy would ever engage.
 *
 * [RequestBatteryExemption]'s system dialog only covers stock Android's
 * own battery-optimization allowlist — it does nothing for a separate,
 * OEM-specific "autostart"/"background power consumption" toggle these
 * skins layer underneath it, which no Android API can query or set
 * programmatically. There's no app-code fix for this; this dialog is the
 * "show the user where to go fix it themselves" alternative.
 *
 * Each OEM's settings Activity name is undocumented and genuinely changes
 * across ROM versions — every launch attempt is wrapped so a stale/renamed
 * intent falls back to the app's own details page instead of crashing.
 */
@Composable
fun OemBackgroundKillOnboarding(viewModel: OemPromptViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val guidance = remember { oemGuidanceFor(Build.MANUFACTURER) } ?: return
    var dismissed by remember { mutableStateOf(viewModel.appPreferences.oemBackgroundKillPromptShown) }
    if (dismissed) return

    fun dismiss() {
        viewModel.appPreferences.oemBackgroundKillPromptShown = true
        dismissed = true
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Keep VENTUS running in the background") },
        text = { Text(guidance.message) },
        confirmButton = {
            TextButton(onClick = {
                openOemSettings(context, guidance)
                dismiss()
            }) { Text("OPEN SETTINGS") }
        },
        dismissButton = {
            TextButton(onClick = ::dismiss) { Text("LATER") }
        },
    )
}

private data class OemGuidance(
    val message: String,
    val intentComponent: Pair<String, String>? = null,
)

private fun oemGuidanceFor(manufacturer: String): OemGuidance? =
    when (manufacturer.trim().lowercase()) {
        "vivo" -> {
            OemGuidance(
                message =
                    "vivo aggressively kills background apps by default. Open " +
                        "i-Manager → App Manager → Autostart Manager and " +
                        "enable VENTUS, and set its battery usage to \"No " +
                        "restrictions\" — otherwise the live scorer can stop " +
                        "updating while your screen is off.",
                // com.vivo.permissionmanager (the commonly-cited package in
                // dontkillmyapp.com-style guides) isn't even installed on
                // current vivo firmware - this OEM's autostart/background
                // settings now live inside i-Manager itself
                // (com.vivo.imanager, internally still com.iqoo.secure.*
                // from vivo's iQOO codebase lineage). Its exact "Autostart
                // Manager" sub-screen isn't an exported Activity on that ROM
                // either (not present in `dumpsys package`'s resolvable
                // intent table), so this targets i-Manager's own main
                // settings entry point instead, one manual tap from the real
                // destination - still better than the generic App Info
                // fallback.
                intentComponent = "com.vivo.imanager" to "com.iqoo.secure.MainSettings",
            )
        }

        "xiaomi", "redmi", "poco" -> {
            OemGuidance(
                message =
                    "MIUI/HyperOS aggressively kills background apps by default. " +
                        "Enable \"Autostart\" for VENTUS, add it to Protected apps, " +
                        "and set battery saver to \"No restrictions\" — " +
                        "otherwise the live scorer can stop updating while your " +
                        "screen is off.",
                intentComponent = "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        }

        "huawei", "honor" -> {
            OemGuidance(
                message =
                    "EMUI/HarmonyOS aggressively kills background apps by " +
                        "default. Open Phone Manager → App launch and set " +
                        "VENTUS to \"Manage manually\" with all three toggles " +
                        "(auto-launch, secondary launch, run in background) " +
                        "enabled — otherwise the live scorer can stop " +
                        "updating while your screen is off.",
                intentComponent = "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )
        }

        "oppo", "realme", "oneplus" -> {
            OemGuidance(
                message =
                    "ColorOS/OxygenOS aggressively kills background apps by " +
                        "default. Open Settings → Battery → VENTUS and " +
                        "allow background activity — otherwise the live " +
                        "scorer can stop updating while your screen is off.",
                intentComponent = "com.coloros.safecenter" to "com.coloros.safecenter.permission.startupapp.StartupAppListActivity",
            )
        }

        else -> {
            null
        }
    }

private fun openOemSettings(
    context: android.content.Context,
    guidance: OemGuidance,
) {
    val component = guidance.intentComponent
    val intent =
        if (component != null) {
            Intent().apply { setClassName(component.first, component.second) }
        } else {
            null
        }
    val fallback =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    try {
        if (intent != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(fallback)
        }
    } catch (
        // Catches SecurityException too, not just ActivityNotFoundException -
        // a non-exported target Activity (a real, observed pattern across
        // MIUI/vivo ROM updates, per this file's own vivo comment above)
        // throws SecurityException ("not exported from uid ..."), which is a
        // distinct RuntimeException from ActivityNotFoundException and would
        // otherwise crash the app on the exact OEMs this onboarding exists
        // to help.
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        e: RuntimeException,
    ) {
        // The OEM's settings Activity name is undocumented and changes across
        // ROM versions - falling back to the app's own details page (which
        // always resolves) beats crashing on a stale component name.
        context.startActivity(fallback)
    }
}
