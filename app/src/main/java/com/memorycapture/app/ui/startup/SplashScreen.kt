package com.memorycapture.app.ui.startup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memorycapture.app.R
import com.memorycapture.app.ui.components.PremiumBackground
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    subtitle: String,
) {
    val transition = rememberInfiniteTransition(label = "premiumStartup")
    val progress = remember { Animatable(0f) }
    var stage by remember { mutableIntStateOf(0) }

    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outerRotation",
    )

    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "innerRotation",
    )

    val logoTilt by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoTilt",
    )

    val logoPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoPulse",
    )

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(3_000, easing = LinearEasing),
        )
    }

    LaunchedEffect(Unit) {
        stage = 0
        delay(900)
        stage = 1
        delay(950)
        stage = 2
        delay(850)
        stage = 3
    }

    PremiumBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .blur(70.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(188.dp)
                            .graphicsLayer {
                                rotationZ = outerRotation
                                rotationX = 64f
                                cameraDistance = 24f * density
                                alpha = 0.70f
                            }
                            .border(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                        Color.Transparent,
                                    ),
                                ),
                                CircleShape,
                            ),
                    )

                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .graphicsLayer {
                                rotationZ = innerRotation
                                rotationY = 66f
                                cameraDistance = 24f * density
                                alpha = 0.52f
                            }
                            .border(
                                1.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.tertiary,
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                ),
                                CircleShape,
                            ),
                    )

                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .graphicsLayer {
                                rotationY = logoTilt
                                rotationX = -logoTilt * 0.42f
                                scaleX = logoPulse
                                scaleY = logoPulse
                                cameraDistance = 28f * density
                            }
                            .shadow(34.dp, RoundedCornerShape(34.dp))
                            .clip(RoundedCornerShape(34.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoCameraBack,
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                            tint = Color.White,
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                translationX = 72f
                                translationY = -70f
                                rotationZ = -outerRotation
                                alpha = 0.86f
                            },
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Text(
                    text = "MemoryCapture",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(34.dp))

                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .width(230.dp)
                        .height(5.dp)
                        .clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                )

                Spacer(Modifier.height(14.dp))

                AnimatedContent(
                    targetState = stage,
                    label = "startupStage",
                ) { current ->
                    Text(
                        text = when (current) {
                            0 -> stringResource(R.string.startup_stage_initializing)
                            1 -> stringResource(R.string.startup_stage_preparing)
                            2 -> stringResource(R.string.startup_stage_optimizing)
                            else -> stringResource(R.string.startup_stage_ready)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
