package com.snapdown.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pilo theme: black + electric lime. One dark look everywhere — the app
// has no light variant anymore, so the old green/blue palette is gone.
val PiloLime = Color(0xFFC7FF2E)
val PiloBlack = Color(0xFF0F0F0F)
val PiloCard = Color(0xFF1C1C1C)
val PiloCardAlt = Color(0xFF2E2E2E)
val PiloGrey = Color(0xFF808080)
val PiloWhite = Color(0xFFFFFFFF)

private val PiloScheme = darkColorScheme(
    primary = PiloLime,
    secondary = PiloLime,
    background = PiloBlack,
    surface = PiloCard,
    surfaceVariant = PiloCardAlt,
    onBackground = PiloWhite,
    onSurface = PiloWhite,
    onPrimary = PiloBlack,
)

@Composable
fun SnapDownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PiloScheme,
        content = content,
    )
}
