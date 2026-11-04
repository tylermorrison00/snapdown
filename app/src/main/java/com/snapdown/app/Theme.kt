package com.snapdown.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium palette: blue-purple gradient accents, clean in both themes.
private val Purple = Color(0xFF7C3AED)
private val Blue = Color(0xFF2563EB)
private val Cyan = Color(0xFF00D4FF)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    secondary = Purple,
    background = Color(0xFF0B1220),
    surface = Color(0xFF131E33),
    surfaceVariant = Color(0xFF1A2947),
    onBackground = Color(0xFFE8EEFC),
    onSurface = Color(0xFFE8EEFC),
)

private val LightScheme = lightColorScheme(
    primary = Blue,
    secondary = Purple,
    background = Color(0xFFE8EEF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDBE6F5),
    onBackground = Color(0xFF0F1A2E),
    onSurface = Color(0xFF0F1A2E),
)

@Composable
fun SnapDownTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
