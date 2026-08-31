package com.example.visionbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VisionBridgeColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = BgPrimary,
    primaryContainer = AccentDim,
    secondary = BgSecondary,
    onSecondary = TextPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgSecondary,
    onSurfaceVariant = TextMuted,
    error = Emergency,
    onError = TextPrimary,
    errorContainer = EmergencyDim,
    outline = Border,
)

@Composable
fun VisionbridgeTheme(
    content: @Composable () -> Unit
) {
    // We enforce the dark color scheme as this is the brand identity and best for low-vision contrast
    MaterialTheme(
        colorScheme = VisionBridgeColorScheme,
        typography = Typography,
        content = content
    )
}