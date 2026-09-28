package com.memorycapture.app.ui.capture

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.recording.CountdownStore
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.ui.components.CountdownDialog
import com.memorycapture.app.ui.components.PremiumBackground
import com.memorycapture.app.ui.components.PrimaryRecordingButton
import com.memorycapture.app.ui.components.RecordingStatusCard
import com.memorycapture.app.ui.components.rememberRecordingElapsed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val storageLabel by preferences.storageLabel.collectAsStateWithLifecycle(initialValue = null)
    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
    val countdown by CountdownStore.seconds.collectAsStateWithLifecycle()
    val startedAt by RecordingSessionStore.startedAtElapsedRealtime.collectAsStateWithLifecycle()
    val elapsed = rememberRecordingElapsed(startedAt)

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val label = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
            scope.launch {
                preferences.setStorageTree(uri.toString(), label)
            }
        }
    }

    countdown?.let {
        CountdownDialog(
            seconds = it,
            title = stringResource(R.string.countdown_title),
        )
    }

    PremiumBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.capture_tab),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                stringResource(R.string.capture_ready_message),
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(14.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(22.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Default.VideoCameraBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.control_center),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.recording_profile),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                )
                            }
                        }
                    }
                }

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
                        text = stringResource(R.string.recording_profile),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }

                item {
                    CaptureOptionCard(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.storage_location),
                        value = storageLabel ?: stringResource(R.string.default_storage),
                    ) {
                        OutlinedButton(onClick = { folderPicker.launch(null) }) {
                            Text(stringResource(R.string.change))
                        }
                    }
                }

                item {
                    CaptureOptionCard(
                        icon = Icons.Default.HighQuality,
                        title = stringResource(R.string.quality),
                        value = stringResource(R.string.quality_1080p),
                    )
                }

                item {
                    CaptureOptionCard(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.frame_rate),
                        value = stringResource(R.string.fps_30),
                    )
                }

                item {
                    CaptureOptionCard(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.audio),
                        value = stringResource(R.string.no_audio_coming_soon),
                    )
                }

                item {
                    CaptureOptionCard(
                        icon = Icons.Default.AllInclusive,
                        title = stringResource(R.string.recording_duration),
                        value = stringResource(R.string.unlimited_until_stopped),
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.unlimited_device_limits_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action?.invoke()
        }
    }
}
