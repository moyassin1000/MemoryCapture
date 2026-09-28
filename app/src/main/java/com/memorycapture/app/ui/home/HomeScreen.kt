package com.memorycapture.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.recording.CountdownStore
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.ui.components.CountdownDialog
import com.memorycapture.app.ui.components.PrimaryRecordingButton
import com.memorycapture.app.ui.components.RecordingStatusCard
import com.memorycapture.app.ui.components.rememberRecordingElapsed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
    val countdown by CountdownStore.seconds.collectAsStateWithLifecycle()
    val savedRecording by SavedRecordingStore.recording.collectAsStateWithLifecycle()
    val startedAt by RecordingSessionStore.startedAtElapsedRealtime.collectAsStateWithLifecycle()
    val elapsed = rememberRecordingElapsed(startedAt)

    countdown?.let {
        CountdownDialog(
            seconds = it,
            title = stringResource(R.string.countdown_title),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                RecordingStatusCard(
                    state = state,
                    elapsed = elapsed,
                    readyLabel = stringResource(R.string.ready_to_record),
                    recordingLabel = stringResource(R.string.status_recording),
                    savingLabel = stringResource(R.string.status_processing),
                    savedLabel = stringResource(R.string.status_completed),
                    errorLabel = stringResource(R.string.status_error),
                )
            }

            item {
                val active = state is RecordingState.Recording || state is RecordingState.Paused
                val busy = state is RecordingState.Preparing ||
                    state is RecordingState.PermissionRequired ||
                    state is RecordingState.Countdown ||
                    state is RecordingState.Stopping ||
                    state is RecordingState.Processing

                PrimaryRecordingButton(
                    active = active,
                    enabled = !busy,
                    startText = stringResource(R.string.start_recording),
                    stopText = stringResource(R.string.stop_recording),
                    onClick = if (active) onStopRecording else onStartRecording,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.quick_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickInfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.HighQuality,
                        label = stringResource(R.string.quality),
                        value = stringResource(R.string.quality_1080p),
                    )
                    QuickInfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = stringResource(R.string.frame_rate),
                        value = stringResource(R.string.fps_30),
                    )
                    QuickInfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AudioFile,
                        label = stringResource(R.string.audio),
                        value = stringResource(R.string.no_audio),
                    )
                }
            }

            savedRecording?.let { saved ->
                item {
                    Text(
                        text = stringResource(R.string.last_recording),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = saved.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = saved.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.unlimited_recording_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickInfoCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
