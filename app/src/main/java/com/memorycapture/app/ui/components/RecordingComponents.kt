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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
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
