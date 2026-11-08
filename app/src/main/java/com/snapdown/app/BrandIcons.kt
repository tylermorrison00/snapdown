package com.snapdown.app

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Platform brand marks, drawn by hand (no brand drawables in the repo).
// Preview (pilo space) shows the real brand glyphs at 16px inside the
// Connect buttons, and 32px white glyphs on brand-coloured tiles on the
// connect page / clear-login rows. Glyphs are drawn in `color`; the YouTube
// play triangle is cut out with `cutout` (the surface behind the glyph).

@Composable
fun BrandGlyph(platformId: String, color: Color, cutout: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        when (platformId) {
            "youtube" -> {
                // Rounded rect + play triangle (preview SVG, simplified).
                drawRoundRect(color = color, cornerRadius = CornerRadius(s * 0.22f, s * 0.22f))
                val tri = Path().apply {
                    moveTo(s * 0.41f, s * 0.33f)
                    lineTo(s * 0.41f, s * 0.67f)
                    lineTo(s * 0.68f, s * 0.50f)
                    close()
                }
                drawPath(tri, color = cutout)
            }
            "instagram" -> {
                // Rounded square outline + lens circle + dot.
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(s * 0.28f, s * 0.28f),
                    style = Stroke(width = s * 0.09f),
                )
                drawCircle(
                    color = color,
                    radius = s * 0.17f,
                    center = Offset(s * 0.5f, s * 0.5f),
                    style = Stroke(width = s * 0.09f),
                )
                drawCircle(color = color, radius = s * 0.055f, center = Offset(s * 0.72f, s * 0.28f))
            }
            "facebook" -> {
                // Bold "f" — same letterform as the brand mark.
                val paint = Paint().apply {
                    this.color = color.toArgb()
                    textSize = s * 0.78f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("f", s * 0.5f, s * 0.5f + s * 0.28f, paint)
            }
            "tiktok" -> {
                // Musical note: head + stem + flag.
                drawCircle(color = color, radius = s * 0.14f, center = Offset(s * 0.38f, s * 0.76f))
                drawLine(
                    color = color,
                    start = Offset(s * 0.52f, s * 0.74f),
                    end = Offset(s * 0.52f, s * 0.16f),
                    strokeWidth = s * 0.10f,
                    cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(s * 0.52f, s * 0.16f)
                    quadraticTo(s * 0.78f, s * 0.22f, s * 0.80f, s * 0.42f)
                    quadraticTo(s * 0.68f, s * 0.32f, s * 0.52f, s * 0.34f)
                    close()
                }
                drawPath(flag, color = color)
            }
        }
    }
}

// Brand-coloured tile (preview: 58px, radius 17px on connect page;
// 40px, radius 11px on clear-login rows). Glyph is white on the tile.
@Composable
fun PlatformTile(platformId: String, size: Dp, corner: Dp) {
    val tileModifier = when (platformId) {
        "youtube" -> Modifier.background(Color(0xFFFF0000))
        "instagram" -> Modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFFF9CE34), Color(0xFFEE2A7B), Color(0xFF6228D7)),
            ),
        )
        "facebook" -> Modifier.background(Color(0xFF1877F2))
        // TikTok: black tile with a dark border (preview .plat-icon.tt).
        "tiktok" -> Modifier.background(Color.Black).border(1.5.dp, Color(0xFF444444), RoundedCornerShape(corner))
        else -> Modifier.background(PiloCardAlt)
    }
    val tileBg = when (platformId) {
        "youtube" -> Color(0xFFFF0000)
        "facebook" -> Color(0xFF1877F2)
        "tiktok" -> Color.Black
        else -> PiloCardAlt
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .then(tileModifier),
        contentAlignment = Alignment.Center,
    ) {
        BrandGlyph(platformId = platformId, color = Color.White, cutout = tileBg, size = size * 0.55f)
    }
}
