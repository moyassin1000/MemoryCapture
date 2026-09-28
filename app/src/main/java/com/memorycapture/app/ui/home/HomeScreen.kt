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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.recordings.RecordingRepository
import com.memorycapture.app.recording.CountdownStore
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.ui.components.CountdownDialog
import com.memorycapture.app.ui.components.PremiumBackground
import com.memorycapture.app.ui.components.PrimaryRecordingButton
import com.memorycapture.app.ui.components.RecordingStatusCard
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
) {
    val context = LocalContext.current
    val repository = remember { RecordingRepository(context.applicationContext) }
    val preferences = remember { AppPreferences(context.applicationContext) }
    val storageTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val storageLabel by preferences.storageLabel.collectAsStateWithLifecycle(initialValue = null)

    val state by RecordingStateStore.state.collectAsStateWithLifecycle()
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
                                text = stringResource(R.string.home_tagline),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(650)) +
                    slideInVertically(
                        animationSpec = tween(700),
                        initialOffsetY = { it / 8 },
                    ),
            ) {
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
                            text = stringResource(R.string.dashboard),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DashboardCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.VideoLibrary,
                                label = stringResource(R.string.recording_count),
                                value = recordingCount.toString(),
                                onClick = onOpenRecordings,
                            )
                            DashboardCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Storage,
                                label = stringResource(R.string.used_storage),
                                value = formatStorage(usedStorage),
                            )
                            DashboardCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.FolderOpen,
                                label = stringResource(R.string.available_storage),
                                value = formatStorage(availableBytes),
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.quick_actions),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.VideoCameraBack,
                                title = stringResource(R.string.capture_tab),
                                onClick = onOpenCapture,
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.VideoLibrary,
                                title = stringResource(R.string.recordings_tab),
                                onClick = onOpenRecordings,
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Settings,
                                title = stringResource(R.string.settings),
                                onClick = onOpenSettings,
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.quick_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
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

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(10.dp, MaterialTheme.shapes.large),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.storage_location),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = storageLabel ?: stringResource(R.string.default_storage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    savedRecording?.let { saved ->
                        item {
                            Text(
                                text = stringResource(R.string.last_recording),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(10.dp, MaterialTheme.shapes.large)
                                    .clickable(onClick = onOpenRecordings),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
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
                                            fontWeight = FontWeight.Bold,
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
                            modifier = Modifier.padding(bottom = 28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .shadow(8.dp, MaterialTheme.shapes.medium)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .shadow(8.dp, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 19.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
    Card(
        modifier = modifier.shadow(7.dp, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
