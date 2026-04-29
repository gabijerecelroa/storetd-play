package com.storetd.play.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFFE50914)
private val Secondary = Color(0xFF2E2B3A)
private val BackgroundDark = Color(0xFF050505)

private val StoreTdDarkScheme: ColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = BackgroundDark,
    surface = Color(0xFF16161A),
    surfaceVariant = Color(0xFF302D38),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1014),
    onError = Color.White,
    onErrorContainer = Color.White
)

@Composable
fun StoreTdPlayTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StoreTdDarkScheme,
        content = content
    )
}
