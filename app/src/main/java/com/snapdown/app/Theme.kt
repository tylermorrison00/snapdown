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

// Preview-matched tones (pilo space, live v40): row backgrounds, meta text,
// dark text on lime fill, disconnect red, hairlines, nav greys.
val PiloRow = Color(0xFF1E1E1E)
val PiloMeta = Color(0xFF9A9A94)
val PiloDarkOnLime = Color(0xFF3D4D10)
val PiloDisconnect = Color(0xFFFF7A6E)
val PiloDisconnectBorder = Color(0xFF4A2C28)
val PiloBorder = Color(0xFF2A2A2A)
val PiloBorderLight = Color(0xFF3A3A3A)
val PiloHairline = Color(0xFF2C2C2C)
val PiloNavGrey = Color(0xFFECECEA)
val PiloPlaceholder = Color(0xFF8B8B84)
val PiloSubtle = Color(0xFF6F6F6A)
val PiloIconGrey = Color(0xFFE8E8E2)
val PiloLabelWhite = Color(0xFFF2F2EC)
val PiloDarkCircle = Color(0xFF232323)
val PiloDarkCircleBorder = Color(0xFF333333)

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
