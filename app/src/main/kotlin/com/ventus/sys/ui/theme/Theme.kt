package com.ventus.sys.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// VENTUS is dark-only by design (matches the desktop app, which has no light
// theme) — isSystemInDarkTheme() is intentionally unused; this always applies
// the same neon-on-void palette regardless of the phone's system setting.
private val VentusColorScheme =
    darkColorScheme(
        primary = VentusQuincy,
        secondary = VentusNeon,
        error = VentusBankai,
        background = VentusVoid,
        surface = VentusSurface,
        surfaceVariant = VentusPanel,
        onPrimary = VentusVoid,
        onBackground = VentusText,
        onSurface = VentusText,
    )

@Composable
fun VentusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VentusColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
