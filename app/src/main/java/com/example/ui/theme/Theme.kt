package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = DeepSpace,
    primaryContainer = SurfaceCardElevated,
    onPrimaryContainer = CyberCyan,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = SurfaceCard,
    onSecondaryContainer = CosmicMagenta,
    tertiary = CosmicMagenta,
    onTertiary = Color.White,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = DarkCosmos,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = StateError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Premium futuristic dark interface requested
    dynamicColor: Boolean = false, // Enforce cohesive cyberpunk dark palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
