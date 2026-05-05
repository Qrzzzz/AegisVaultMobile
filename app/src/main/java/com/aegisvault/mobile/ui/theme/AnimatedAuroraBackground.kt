package com.aegisvault.mobile.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class AuroraBlob(
    val color: Color,
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val moveX: Float,
    val moveY: Float,
    val speed: Float,
    val phase: Float,
    val alpha: Float,
)

@Composable
fun AnimatedAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora_transition")
    val isDark = isSystemInDarkTheme()

    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 42000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora_progress",
    )

    val baseColors = remember(isDark) {
        if (isDark) {
            listOf(
                AuroraBlob(
                    color = Color(0xFF5B7CFF),
                    baseX = 0.18f,
                    baseY = 0.24f,
                    radius = 0.72f,
                    moveX = 0.13f,
                    moveY = 0.10f,
                    speed = 1.00f,
                    phase = 0.2f,
                    alpha = 0.28f,
                ),
                AuroraBlob(
                    color = Color(0xFF8266E8),
                    baseX = 0.78f,
                    baseY = 0.22f,
                    radius = 0.68f,
                    moveX = 0.10f,
                    moveY = 0.14f,
                    speed = 0.72f,
                    phase = 1.7f,
                    alpha = 0.25f,
                ),
                AuroraBlob(
                    color = Color(0xFFD56497),
                    baseX = 0.70f,
                    baseY = 0.74f,
                    radius = 0.62f,
                    moveX = 0.12f,
                    moveY = 0.09f,
                    speed = 0.56f,
                    phase = 2.9f,
                    alpha = 0.18f,
                ),
                AuroraBlob(
                    color = Color(0xFFD57A61),
                    baseX = 0.22f,
                    baseY = 0.78f,
                    radius = 0.56f,
                    moveX = 0.09f,
                    moveY = 0.11f,
                    speed = 0.44f,
                    phase = 4.1f,
                    alpha = 0.14f,
                ),
                AuroraBlob(
                    color = Color(0xFF3FB5CC),
                    baseX = 0.48f,
                    baseY = 0.50f,
                    radius = 0.78f,
                    moveX = 0.08f,
                    moveY = 0.07f,
                    speed = 0.34f,
                    phase = 5.4f,
                    alpha = 0.16f,
                ),
            )
        } else {
            listOf(
                AuroraBlob(
                    color = Color(0xFF8AA2F5),
                    baseX = 0.20f,
                    baseY = 0.25f,
                    radius = 0.72f,
                    moveX = 0.10f,
                    moveY = 0.08f,
                    speed = 0.95f,
                    phase = 0.2f,
                    alpha = 0.18f,
                ),
                AuroraBlob(
                    color = Color(0xFFB195EE),
                    baseX = 0.78f,
                    baseY = 0.24f,
                    radius = 0.65f,
                    moveX = 0.08f,
                    moveY = 0.10f,
                    speed = 0.68f,
                    phase = 1.8f,
                    alpha = 0.15f,
                ),
                AuroraBlob(
                    color = Color(0xFFDD9AB3),
                    baseX = 0.70f,
                    baseY = 0.72f,
                    radius = 0.58f,
                    moveX = 0.10f,
                    moveY = 0.08f,
                    speed = 0.52f,
                    phase = 2.7f,
                    alpha = 0.12f,
                ),
                AuroraBlob(
                    color = Color(0xFFE0A28D),
                    baseX = 0.24f,
                    baseY = 0.78f,
                    radius = 0.50f,
                    moveX = 0.08f,
                    moveY = 0.09f,
                    speed = 0.42f,
                    phase = 4.1f,
                    alpha = 0.10f,
                ),
                AuroraBlob(
                    color = Color(0xFF8FC8D6),
                    baseX = 0.50f,
                    baseY = 0.48f,
                    radius = 0.74f,
                    moveX = 0.07f,
                    moveY = 0.06f,
                    speed = 0.34f,
                    phase = 5.3f,
                    alpha = 0.10f,
                ),
            )
        }
    }

    val backgroundGradient = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF070812),
                Color(0xFF0B1024),
                Color(0xFF090816),
            )
        } else {
            listOf(
                Color(0xFFF6F1EA),
                Color(0xFFF0ECEB),
                Color(0xFFF7F3EE),
            )
        }
    }

    val topOverlay = remember(isDark) {
        if (isDark) {
            listOf(
                Color.Black.copy(alpha = 0.28f),
                Color.Black.copy(alpha = 0.08f),
                Color.Black.copy(alpha = 0.34f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.20f),
                Color.Transparent,
                Color(0xFFF4EEE8).copy(alpha = 0.34f),
            )
        }
    }

    val vignetteEnd = if (isDark) Color.Black.copy(alpha = 0.30f) else Color(0xFFCCBFB3).copy(alpha = 0.14f)
    val baseBackground = if (isDark) Color(0xFF070812) else Color(0xFFF7F3EE)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(baseBackground),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp),
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = backgroundGradient,
                    startY = 0f,
                    endY = size.height,
                ),
            )

            val minSide = minOf(size.width, size.height)
            val fullTurn = (PI * 2.0).toFloat()

            baseColors.forEach { blob ->
                val angle = fullTurn * progress * blob.speed + blob.phase

                val driftX =
                    sin(angle) * size.width * blob.moveX +
                        cos(angle * 0.47f) * size.width * 0.045f

                val driftY =
                    cos(angle * 0.81f) * size.height * blob.moveY +
                        sin(angle * 0.33f) * size.height * 0.04f

                val center = Offset(
                    x = size.width * blob.baseX + driftX,
                    y = size.height * blob.baseY + driftY,
                )

                val radius = minSide * blob.radius

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to blob.color.copy(alpha = blob.alpha),
                            0.42f to blob.color.copy(alpha = blob.alpha * 0.55f),
                            0.72f to blob.color.copy(alpha = blob.alpha * 0.18f),
                            1.00f to Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                    blendMode = BlendMode.Plus,
                )
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = topOverlay,
                    startY = 0f,
                    endY = size.height,
                ),
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        vignetteEnd,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.maxDimension * 0.82f,
                ),
            )
        }

        content()
    }
}
