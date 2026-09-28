package com.memorycapture.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorycapture.app.recording.RecordingState
import kotlinx.coroutines.delay

@Composable
fun RecordingStatusCard(
    state: RecordingState,
    elapsed: String?,
    readyLabel: String,
    recordingLabel: String,
    savingLabel: String,
    savedLabel: String,
    errorLabel: String,
) {
    val (label, accent) = when (state) {
        RecordingState.Recording, RecordingState.Paused ->
            recordingLabel to MaterialTheme.colorScheme.error
        RecordingState.Stopping, RecordingState.Processing ->
            savingLabel to MaterialTheme.colorScheme.primary
        RecordingState.Completed -> savedLabel to Color(0xFF2FB56F)
        is RecordingState.Error -> errorLabel to MaterialTheme.colorScheme.error
        else -> readyLabel to Color(0xFF2FB56F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●", color = accent)
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (elapsed != null) {
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
fun RecordOrb(
    active: Boolean,
    enabled: Boolean,
    label: String,
    elapsed: String?,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "recordOrb")

    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (active) 1.08f else 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 760 else 1600,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordOrbPulse",
    )

    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "recordOrbRing",
    )

    val accent = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accent2 = if (active) Color(0xFFFF6C79) else MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(186.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        rotationZ = ringRotation
                        alpha = if (enabled) 0.7f else 0.28f
                    }
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.85f),
                                accent2,
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            Box(
                modifier = Modifier
                    .size(142.dp)
                    .blur(24.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = if (enabled) 0.38f else 0.12f
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accent.copy(alpha = 0.72f),
                                accent2.copy(alpha = 0.34f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        scaleX = if (enabled) pulse else 1f
                        scaleY = if (enabled) pulse else 1f
                        alpha = if (enabled) 1f else 0.5f
                    }
                    .shadow(28.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(accent, accent2),
                        ),
                    )
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (active) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = label,
                    modifier = Modifier.size(if (active) 50.dp else 58.dp),
                    tint = Color.White,
                )
            }
        }

        Text(
            text = elapsed ?: label,
            style = if (elapsed != null) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Black,
            color = if (active) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PrimaryRecordingButton(
    active: Boolean,
    enabled: Boolean,
    startText: String,
    stopText: String,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "recordButton")
    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = if (active) 1.02f else 1.008f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordButtonPulse",
    )

    val colors = if (active) {
        listOf(
            MaterialTheme.colorScheme.error,
            Color(0xFFFF5A67),
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (enabled) pulse else 1f
                scaleY = if (enabled) pulse else 1f
                alpha = if (enabled) 1f else 0.48f
            }
            .shadow(18.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = if (active) stopText else startText,
            modifier = Modifier.padding(start = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun CountdownDialog(
    seconds: Int,
    title: String,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            AnimatedContent(
                targetState = seconds,
                label = "countdown",
            ) { value ->
                Text(
                    text = value.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
fun rememberRecordingElapsed(startedAt: Long?): String? {
    var now by remember(startedAt) { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    if (startedAt == null) return null
    val seconds = ((now - startedAt) / 1000L).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}
