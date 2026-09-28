package com.memorycapture.app.ui.home

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.billing.ProBillingManager
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.AudioMode
import com.memorycapture.app.data.recordings.RecordingRepository
import com.memorycapture.app.recording.CountdownStore
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.ui.components.CountdownDialog
import com.memorycapture.app.ui.components.PremiumBackground
import com.memorycapture.app.ui.components.RecordOrb
import com.memorycapture.app.ui.components.rememberRecordingElapsed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { RecordingRepository(context.applicationContext) }
    val preferences = remember { AppPreferences(context.applicationContext) }

    val storageTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val audioMode by preferences.audioMode.collectAsStateWithLifecycle(initialValue = AudioMode.DeviceAndMic)
    val storageLabel by preferences.storageLabel.collectAsStateWithLifecycle(initialValue = null)
    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
    val proState by ProBillingManager.state.collectAsStateWithLifecycle()
    val countdown by CountdownStore.seconds.collectAsStateWithLifecycle()
    val savedRecording by SavedRecordingStore.recording.collectAsStateWithLifecycle()
    val startedAt by RecordingSessionStore.startedAtElapsedRealtime.collectAsStateWithLifecycle()
    val elapsed = rememberRecordingElapsed(startedAt)

    var recordingCount by remember { mutableStateOf(0) }
    var usedStorage by remember { mutableLongStateOf(0L) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        contentVisible = true
    }

    LaunchedEffect(storageTreeUri, savedRecording?.displayName) {
        val recordings = repository.loadRecordings(storageTreeUri)
        recordingCount = recordings.size
        usedStorage = recordings.sumOf { it.sizeBytes }
    }

    countdown?.let {
        CountdownDialog(
            seconds = it,
            title = stringResource(R.string.countdown_title),
        )
    }

    val availableBytes = remember {
        runCatching {
            StatFs(Environment.getDataDirectory().absolutePath).availableBytes
        }.getOrDefault(0L)
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
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = if (proState.isPro) {
                                    stringResource(R.string.home_pro_member)
                                } else {
                                    stringResource(R.string.home_control_center_subtitle)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenPro) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = stringResource(R.string.pro_title),
                                tint = if (proState.isPro) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(520)) +
                    slideInVertically(
                        animationSpec = tween(620),
                        initialOffsetY = { it / 10 },
                    ),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(20.dp, MaterialTheme.shapes.large),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 28.dp, horizontal = 22.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = stringResource(
                                            if (active) R.string.status_recording else R.string.ready_to_record,
                                        ),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = stringResource(R.string.home_recording_profile_line),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                RecordOrb(
                                    active = active,
                                    enabled = !busy,
                                    label = stringResource(
                                        if (active) R.string.stop_recording else R.string.start_recording,
                                    ),
                                    elapsed = elapsed,
                                    onClick = if (active) onStopRecording else onStartRecording,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    ProfileMetric("1080p", stringResource(R.string.quality))
                                    ProfileMetric("30", stringResource(R.string.frame_rate))
                                    ProfileMetric(
                                        homeAudioLabel(audioMode),
                                        stringResource(R.string.audio),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.home_library_overview),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OverviewCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.VideoLibrary,
                                value = recordingCount.toString(),
                                label = stringResource(R.string.recording_count),
                                onClick = onOpenRecordings,
                            )
                            OverviewCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Storage,
                                value = formatStorage(usedStorage),
                                label = stringResource(R.string.used_storage),
                            )
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(10.dp, MaterialTheme.shapes.large)
                                .clickable(onClick = onOpenCapture),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = storageLabel ?: stringResource(R.string.default_storage),
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.home_available_storage_value,
                                            formatStorage(availableBytes),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(10.dp, MaterialTheme.shapes.large)
                                .clickable(onClick = onOpenPro),
                            colors = CardDefaults.cardColors(
                                containerColor = if (proState.isPro) {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (proState.isPro) {
                                            stringResource(R.string.pro_active)
                                        } else {
                                            stringResource(R.string.upgrade_to_pro)
                                        },
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        text = if (proState.isPro) {
                                            stringResource(R.string.pro_active_body)
                                        } else {
                                            stringResource(R.string.upgrade_to_pro_body)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.pro_badge),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }

                    savedRecording?.let { saved ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenRecordings),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.last_recording),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(saved.displayName, fontWeight = FontWeight.Bold)
                                    Text(
                                        saved.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.unlimited_recording_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 30.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetric(
    value: String,
    label: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .shadow(8.dp, MaterialTheme.shapes.medium)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatStorage(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes / 1024.0 / 1024.0
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}


@Composable
private fun homeAudioLabel(mode: AudioMode): String =
    when (mode) {
        AudioMode.None -> stringResource(R.string.audio_mode_off)
        AudioMode.Microphone -> stringResource(R.string.audio_mode_mic)
        AudioMode.DeviceAudio -> stringResource(R.string.audio_mode_device)
        AudioMode.DeviceAndMic -> stringResource(R.string.audio_mode_both)
    }
