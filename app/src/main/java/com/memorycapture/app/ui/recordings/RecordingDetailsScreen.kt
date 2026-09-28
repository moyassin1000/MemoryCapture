package com.memorycapture.app.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.recordings.RecordingItem
import com.memorycapture.app.data.recordings.RecordingRepository
import kotlinx.coroutines.launch
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
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recording_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                Text(stringResource(R.string.recording_not_found))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            recording.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                DetailRow(stringResource(R.string.duration), formatDuration(recording.durationMillis))
                DetailRow(stringResource(R.string.file_size), formatSize(recording.sizeBytes))
                DetailRow(
                    stringResource(R.string.resolution),
                    if (recording.width > 0 && recording.height > 0) {
                        recording.width.toString() + " × " + recording.height.toString()
                    } else {
                        stringResource(R.string.unknown)
                    },
                )
                DetailRow(
                    stringResource(R.string.date),
                    DateFormat.getDateTimeInstance().format(Date(recording.dateAddedMillis)),
                )

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

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { shareVideo(context, recording.uri) },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(
                        stringResource(R.string.share),
                        modifier = Modifier.padding(start = 8.dp),
                    )
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

@Composable
private fun DetailRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
