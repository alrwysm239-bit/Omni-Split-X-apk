package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = NeonCyanLight,
    secondary = NeonMagenta,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A004F),
    onSecondaryContainer = Color(0xFFFFD7F5),
    tertiary = NeonPurple,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF2E3852)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to stunning dark cyber theme
    dynamicColor: Boolean = false, // Keep signature design from screenshot
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
