package com.memorycapture.app.ui.capture

import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.AudioMode
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
    val audioMode by preferences.audioMode.collectAsStateWithLifecycle(
        initialValue = AudioMode.DeviceAndMic,
    )
    val microphoneDeviceId by preferences.microphoneDeviceId.collectAsStateWithLifecycle(
        initialValue = -1,
    )
    val audioManager = remember {
        context.getSystemService(AudioManager::class.java)
    }
    var inputDevices by remember {
        mutableStateOf(
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList(),
        )
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                inputDevices = audioManager
                    .getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .toList()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                inputDevices = audioManager
                    .getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .toList()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }
    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
    val countdown by CountdownStore.seconds.collectAsStateWithLifecycle()
    val startedAt by RecordingSessionStore.startedAtElapsedRealtime.collectAsStateWithLifecycle()
    val pausedAt by RecordingSessionStore.pausedAtElapsedRealtime.collectAsStateWithLifecycle()
    val accumulatedPausedMs by RecordingSessionStore.accumulatedPausedMs.collectAsStateWithLifecycle()
    val elapsed = rememberRecordingElapsed(
        startedAt = startedAt,
        pausedAt = pausedAt,
        accumulatedPausedMs = accumulatedPausedMs,
    )

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

    val active = state is RecordingState.Recording || state is RecordingState.Paused
    val busy = state is RecordingState.Preparing ||
        state is RecordingState.PermissionRequired ||
        state is RecordingState.Countdown ||
        state is RecordingState.Stopping ||
        state is RecordingState.Processing

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
                            .shadow(20.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
                                        ),
                                    ),
                                )
                                .padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Default.VideoCameraBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.control_center),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource(R.string.audio_ready),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ProfilePill(
                                    icon = Icons.Default.HighQuality,
                                    text = "1080p",
                                )
                                ProfilePill(
                                    icon = Icons.Default.Speed,
                                    text = "30 FPS",
                                )
                                ProfilePill(
                                    icon = Icons.Default.GraphicEq,
                                    text = audioModeShortLabel(audioMode),
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
                    PrimaryRecordingButton(
                        active = active,
                        enabled = !busy,
                        startText = stringResource(R.string.start_recording),
                        stopText = stringResource(R.string.stop_recording),
                        onClick = if (active) onStopRecording else onStartRecording,
                    )
                }

                item {
                    AudioStudioCard(
                        selected = audioMode,
                        selectedMicDeviceId = microphoneDeviceId,
                        inputDevices = inputDevices,
                        enabled = !active && !busy,
                        onSelect = { mode ->
                            scope.launch {
                                preferences.setAudioMode(mode)
                            }
                        },
                        onMicrophoneSelect = { deviceId ->
                            scope.launch {
                                preferences.setMicrophoneDeviceId(deviceId)
                            }
                        },
                    )
                }

                item {
                    val overlayGranted = Settings.canDrawOverlays(context)
                    CaptureOptionCard(
                        icon = Icons.Default.AllInclusive,
                        title = stringResource(R.string.floating_controls_title),
                        value = if (overlayGranted) {
                            stringResource(R.string.floating_controls_enabled)
                        } else {
                            stringResource(R.string.floating_controls_disabled)
                        },
                    ) {
                        OutlinedButton(
                            enabled = !active && !busy,
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                context.startActivity(intent)
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (overlayGranted) R.string.floating_controls_manage
                                    else R.string.floating_controls_enable,
                                ),
                            )
                        }
                    }
                    // floating controls permission
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
                        OutlinedButton(
                            enabled = !active && !busy,
                            onClick = { folderPicker.launch(null) },
                        ) {
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
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.audio_capture_limit_note),
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
private fun AudioStudioCard(
    selected: AudioMode,
    selectedMicDeviceId: Int,
    inputDevices: List<AudioDeviceInfo>,
    enabled: Boolean,
    onSelect: (AudioMode) -> Unit,
    onMicrophoneSelect: (Int) -> Unit,
) {
    val internalSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, MaterialTheme.shapes.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_studio),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.audio_studio_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AudioModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Videocam,
                    title = stringResource(R.string.audio_mode_off),
                    selected = selected == AudioMode.None,
                    enabled = enabled,
                    onClick = { onSelect(AudioMode.None) },
                )
                AudioModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.audio_mode_mic),
                    selected = selected == AudioMode.Microphone,
                    enabled = enabled,
                    onClick = { onSelect(AudioMode.Microphone) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AudioModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speaker,
                    title = stringResource(R.string.audio_mode_device),
                    selected = selected == AudioMode.DeviceAudio,
                    enabled = enabled && internalSupported,
                    onClick = { onSelect(AudioMode.DeviceAudio) },
                )
                AudioModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.audio_mode_both),
                    selected = selected == AudioMode.DeviceAndMic,
                    enabled = enabled && internalSupported,
                    onClick = { onSelect(AudioMode.DeviceAndMic) },
                )
            }

            Text(
                text = if (internalSupported) {
                    when (selected) {
                        AudioMode.None -> stringResource(R.string.audio_mode_off_body)
                        AudioMode.Microphone -> stringResource(R.string.audio_mode_mic_body)
                        AudioMode.DeviceAudio -> stringResource(R.string.audio_mode_device_body)
                        AudioMode.DeviceAndMic -> stringResource(R.string.audio_mode_both_body)
                    }
                } else {
                    stringResource(R.string.audio_android10_required)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selected == AudioMode.Microphone || selected == AudioMode.DeviceAndMic) {
                Text(
                    text = stringResource(R.string.microphone_source),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )

                MicrophoneInputCard(
                    title = stringResource(R.string.microphone_source_auto),
                    selected = selectedMicDeviceId < 0,
                    enabled = enabled,
                    onClick = { onMicrophoneSelect(-1) },
                )

                inputDevices.forEach { device ->
                    MicrophoneInputCard(
                        title = device.productName?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.microphone_connected_inputs),
                        selected = selectedMicDeviceId == device.id,
                        enabled = enabled,
                        onClick = { onMicrophoneSelect(device.id) },
                    )
                }
            }
        }
    }
}


@Composable
private fun MicrophoneInputCard(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                },
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.microphone_device_selected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AudioModeCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            },
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
private fun ProfilePill(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun audioModeShortLabel(mode: AudioMode): String =
    when (mode) {
        AudioMode.None -> stringResource(R.string.audio_mode_off)
        AudioMode.Microphone -> stringResource(R.string.audio_mode_mic)
        AudioMode.DeviceAudio -> stringResource(R.string.audio_mode_device)
        AudioMode.DeviceAndMic -> stringResource(R.string.audio_mode_both)
    }

@Composable
private fun CaptureOptionCard(
    icon: ImageVector,
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
