package com.memorycapture.app.ui.recordings

import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.recordings.RecordingItem
import com.memorycapture.app.data.recordings.RecordingRepository
import com.memorycapture.app.ui.components.PremiumBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailsScreen(
    uriString: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { RecordingRepository(context.applicationContext) }
    val preferences = remember { AppPreferences(context.applicationContext) }
    val customTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<RecordingItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(uriString, customTreeUri) {
        item = repository.loadRecordings(customTreeUri).firstOrNull { it.uri == uriString }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_recording)) },
            text = { Text(stringResource(R.string.delete_recording_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            repository.delete(uriString)
                            onBack()
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename_recording)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.file_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        showRename = false
                        scope.launch {
                            repository.rename(uriString, renameText.trim())
                            item = repository.loadRecordings(customTreeUri)
                                .firstOrNull { it.uri == uriString }
                        }
                    },
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
                        Text(
                            stringResource(R.string.recording_details),
                            fontWeight = FontWeight.Black,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            val recording = item

            if (recording == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.recording_not_found),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                val thumbnail = rememberDetailsThumbnail(recording.uri)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        ),
                    ) {
                        Column {
                            if (thumbnail != null) {
                                Box {
                                    Image(
                                        bitmap = thumbnail,
                                        contentDescription = recording.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(62.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.56f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(
                                            onClick = { openVideo(context, recording.uri) },
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = stringResource(R.string.play),
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.VideoFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(58.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = recording.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = DateFormat.getDateTimeInstance()
                                        .format(Date(recording.dateAddedMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailMetric(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.duration),
                            value = formatDuration(recording.durationMillis),
                        )
                        DetailMetric(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.file_size),
                            value = formatSize(recording.sizeBytes),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailMetric(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.resolution),
                            value = if (recording.width > 0 && recording.height > 0) {
                                recording.width.toString() + " × " + recording.height.toString()
                            } else {
                                stringResource(R.string.unknown)
                            },
                        )
                        DetailMetric(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.date),
                            value = DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(recording.dateAddedMillis)),
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openVideo(context, recording.uri) },
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(
                            stringResource(R.string.play),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = { shareVideo(context, recording.uri) },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text(
                                stringResource(R.string.share),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                renameText = recording.displayName.removeSuffix(".mp4")
                                showRename = true
                            },
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                            Text(
                                stringResource(R.string.rename),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDelete = true },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.delete),
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier.shadow(6.dp, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun rememberDetailsThumbnail(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = uriString,
    ) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(uriString),
                        Size(960, 540),
                        null,
                    )
                }.getOrNull()
            } else {
                null
            }
        }
    }
    return bitmap?.asImageBitmap()
}
