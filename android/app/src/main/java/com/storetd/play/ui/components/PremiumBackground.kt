package com.storetd.play.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.premiumStoreTdBackground(): Modifier {
    return this
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070A12),
                    Color(0xFF0B1020),
                    Color(0xFF090B13),
                    Color(0xFF05070C)
                )
            )
        )
        .drawBehind {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x30FF2638),
                        Color(0x16A31830),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.12f, h * 0.12f),
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = Offset(w * 0.12f, h * 0.12f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x253B63FF),
                        Color(0x101E2A66),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.86f, h * 0.18f),
                    radius = w * 0.38f
                ),
                radius = w * 0.38f,
                center = Offset(w * 0.86f, h * 0.18f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x22FF4655),
                        Color(0x0F51101A),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.50f, h * 0.92f),
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = Offset(w * 0.50f, h * 0.92f)
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x18000000),
                        Color.Transparent,
                        Color(0x22000000)
                    )
                )
            )
        }
}
