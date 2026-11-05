package com.snapdown.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium palette: deep green-blue coloring from the approved Home mockup, logo colors only. Layout unchanged.
private val Green = Color(0xFF22C55E)
private val Blue = Color(0xFF2E9BFF)
private val Cyan = Color(0xFF06B6D4)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    secondary = Green,
    background = Color(0xFF0A5C46),
    surface = Color(0xFF0F7A5C),
    surfaceVariant = Color(0xFF146B52),
    onBackground = Color(0xFFE8FFF4),
    onSurface = Color(0xFFE8FFF4),
)

private val LightScheme = lightColorScheme(
    primary = Blue,
    secondary = Green,
    background = Color(0xFFE6F7F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFD6F0E6),
    onBackground = Color(0xFF0B3D2E),
    onSurface = Color(0xFF0B3D2E),
)

@Composable
fun SnapDownTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
