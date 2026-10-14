package com.despeja.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0E7C61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE2),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4A635A),
    secondaryContainer = Color(0xFFDDE8E2),
    tertiary = Color(0xFFB4561F),
    tertiaryContainer = Color(0xFFFFE2D2),
    onTertiaryContainer = Color(0xFF4A1A00),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceVariant = Color(0xFFE3EAE6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF3F1),
    outlineVariant = Color(0xFFD2DBD6),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD6B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF0B5444),
    onPrimaryContainer = Color(0xFFCDEFE2),
    secondary = Color(0xFFB1CCC1),
    secondaryContainer = Color(0xFF33493F),
    tertiary = Color(0xFFFFB68F),
    tertiaryContainer = Color(0xFF6B2E08),
    onTertiaryContainer = Color(0xFFFFE2D2),
    background = Color(0xFF101413),
    surface = Color(0xFF101413),
    surfaceVariant = Color(0xFF2A322F),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF232A27),
    outlineVariant = Color(0xFF3A4440),
    error = Color(0xFFFFB4AB),
)

@Composable
fun DespejaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
