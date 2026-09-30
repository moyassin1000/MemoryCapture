package com.memorycapture.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GlobalRecordingPill(
    elapsed: String?,
    label: String,
    paused: Boolean,
    pauseLabel: String,
    resumeLabel: String,
    stopLabel: String,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(14.dp, RoundedCornerShape(24.dp))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                RoundedCornerShape(24.dp),
            )
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "●",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = elapsed ?: label,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onPauseResume)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (paused) resumeLabel else pauseLabel,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (paused) resumeLabel else pauseLabel,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.error,
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onStop)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = stopLabel,
                tint = Color.White,
            )
            Text(
                text = stopLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
