package com.memorycapture.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore

private data class Choice(@StringRes val label: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
    var audio by remember { mutableStateOf(Choice(R.string.device_and_mic)) }
    var quality by remember { mutableStateOf(Choice(R.string.quality_1080p)) }
    var fps by remember { mutableStateOf(Choice(R.string.fps_30)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                SettingCard(
                    title = stringResource(R.string.audio),
                    value = stringResource(audio.label),
                    options = listOf(
                        Choice(R.string.no_audio),
                        Choice(R.string.microphone),
                        Choice(R.string.device_audio),
                        Choice(R.string.device_and_mic),
                    ),
                    onSelected = { audio = it },
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.quality),
                    value = stringResource(quality.label),
                    options = listOf(
                        Choice(R.string.quality_auto),
                        Choice(R.string.quality_720p),
                        Choice(R.string.quality_1080p),
                        Choice(R.string.quality_1440p),
                    ),
                    onSelected = { quality = it },
                )
            }
            item {
                SettingCard(
                    title = stringResource(R.string.frame_rate),
                    value = stringResource(fps.label),
                    options = listOf(
                        Choice(R.string.fps_auto),
                        Choice(R.string.fps_30),
                        Choice(R.string.fps_60),
                    ),
                    onSelected = { fps = it },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.recording_status, stateLabel(state)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                val active = state is RecordingState.Recording || state is RecordingState.Paused
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (active) onStopRecording else onStartRecording,
                ) {
                    Icon(
                        imageVector = if (active) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(if (active) R.string.stop_recording else R.string.start_recording),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    value: String,
    options: List<Choice>,
    onSelected: (Choice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.take(2).forEach { option ->
                    Button(onClick = { onSelected(option) }) {
                        Text(stringResource(option.label))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.drop(2).forEach { option ->
                    Button(onClick = { onSelected(option) }) {
                        Text(stringResource(option.label))
                    }
                }
            }
        }
    }
}

@Composable
private fun stateLabel(state: RecordingState): String = stringResource(
    when (state) {
        RecordingState.Idle -> R.string.status_idle
        RecordingState.Preparing -> R.string.status_preparing
        RecordingState.PermissionRequired -> R.string.status_permission
        RecordingState.Countdown -> R.string.status_countdown
        RecordingState.Recording -> R.string.status_recording
        RecordingState.Paused -> R.string.status_paused
        RecordingState.Stopping -> R.string.status_stopping
        RecordingState.Processing -> R.string.status_processing
        RecordingState.Completed -> R.string.status_completed
        is RecordingState.Error -> R.string.status_error
    },
)
