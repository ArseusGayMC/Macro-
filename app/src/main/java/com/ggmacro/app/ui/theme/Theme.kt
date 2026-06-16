package com.ggmacro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GamingColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = GamingBlack,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = GamingBlack,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    secondaryContainer = NeonPurpleDim,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonGreen,
    onTertiary = GamingBlack,
    background = GamingBlack,
    onBackground = TextPrimary,
    surface = GamingDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = GamingCard,
    onSurfaceVariant = TextSecondary,
    outline = GamingBorder,
    error = NeonRed,
    onError = TextPrimary
)

@Composable
fun GGMacroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GamingColorScheme,
        typography = GGTypography,
        content = content
    )
}
