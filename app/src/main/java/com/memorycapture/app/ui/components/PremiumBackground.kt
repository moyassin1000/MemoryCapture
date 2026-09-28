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
    val transition = rememberInfiniteTransition(label = "premium-background")
    val drift by transition.animateFloat(
        initialValue = -22f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070A13),
                        Color(0xFF0A1022),
                        Color(0xFF0D1530),
                        Color(0xFF080B15),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = (-72).dp, y = 110.dp)
                .graphicsLayer {
                    translationX = drift
                    scaleX = pulse
                    scaleY = pulse
                    alpha = 0.48f
                }
                .blur(70.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF4B5FFF),
                            Color(0x884B5FFF),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        Box(
            modifier = Modifier
                .size(290.dp)
                .offset(x = 190.dp, y = 410.dp)
                .graphicsLayer {
                    translationY = -drift
                    scaleX = 1.05f / pulse
                    scaleY = 1.05f / pulse
                    alpha = 0.38f
                }
                .blur(86.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF7A5CFA),
                            Color(0x667A5CFA),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        content()
    }
}
