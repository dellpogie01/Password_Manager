package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = TealPrimary,
        onPrimary = DarkNavy,
        primaryContainer = TealDark,
        onPrimaryContainer = TealLight,
        secondary = AccentCyan,
        onSecondary = DarkNavy,
        secondaryContainer = CardNavy,
        onSecondaryContainer = TextWhite,
        background = DeepNavy,
        onBackground = TextWhite,
        surface = SurfaceNavy,
        onSurface = TextWhite,
        surfaceVariant = CardNavy,
        onSurfaceVariant = TextGray,
        error = ErrorRed,
        onError = TextWhite,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = TealDark,
        onPrimary = Color.White,
        primaryContainer = TealLight,
        onPrimaryContainer = DarkNavy,
        secondary = TealPrimary,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0F7FA),
        onSecondaryContainer = DarkNavy,
        background = Color(0xFFF4F6F9),
        onBackground = Color(0xFF0A1128),
        surface = Color.White,
        onSurface = Color(0xFF0A1128),
        surfaceVariant = Color(0xFFE9ECEF),
        onSurfaceVariant = Color(0xFF495057),
        error = ErrorRed,
        onError = Color.White,
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
