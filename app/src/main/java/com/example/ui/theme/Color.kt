package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF090A0F)
val DarkSurface = Color(0xFF12151F)
val DarkSurfaceVariant = Color(0xFF181D2B)
val DarkBorder = Color(0xFF232A3D)
val DarkBorderGlow = Color(0xFF00F0FF)

val NeonCyan = Color(0xFF00F0FF)
val NeonCyanLight = Color(0xFF7DF9FF)
val NeonMagenta = Color(0xFFD946EF)
val NeonPurple = Color(0xFF9333EA)
val NeonGreen = Color(0xFF10B981)
val NeonAmber = Color(0xFFF59E0B)
val NeonRed = Color(0xFFEF4444)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val OmniGradient = Brush.horizontalGradient(
    colors = listOf(NeonMagenta, NeonCyan)
)

val OmniGradientVertical = Brush.verticalGradient(
    colors = listOf(NeonMagenta.copy(alpha = 0.25f), NeonCyan.copy(alpha = 0.05f))
)

val CyberCardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF1A1F30), Color(0xFF0F131D))
)
