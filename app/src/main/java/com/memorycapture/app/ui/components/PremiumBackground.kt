package com.memorycapture.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun PremiumBackground(
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "premiumBackground")

    val drift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.primary.copy(alpha = 0.08f),
                        colors.background,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = (-80).dp, y = 90.dp)
                .graphicsLayer {
                    translationX = drift
                    scaleX = pulse
                    scaleY = pulse
                    alpha = 0.52f
                }
                .blur(72.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.9f),
                            colors.primary.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        Box(
            modifier = Modifier
                .size(310.dp)
                .offset(x = 190.dp, y = 430.dp)
                .graphicsLayer {
                    translationY = -drift
                    scaleX = 1.05f / pulse
                    scaleY = 1.05f / pulse
                    alpha = 0.44f
                }
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.tertiary.copy(alpha = 0.8f),
                            colors.tertiary.copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        content()
    }
}
