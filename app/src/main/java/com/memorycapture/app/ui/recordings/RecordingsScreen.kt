package com.memorycapture.app.ui.recordings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.ui.components.PremiumBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class RecordingSort {
    Newest,
    Oldest,
    Largest,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onOpenDetails: (String) -> Unit,
    onPlayRecording: (String) -> Unit,
    onGoToCapture: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { RecordingRepository(context.applicationContext) }
    val preferences = remember { AppPreferences(context.applicationContext) }
    val customTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val favorites by preferences.favoriteRecordings.collectAsStateWithLifecycle(initialValue = emptySet())
    val saved by SavedRecordingStore.recording.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var recordings by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(RecordingSort.Newest) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var renameTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(customTreeUri, saved?.displayName, refreshKey) {
        recordings = repository.loadRecordings(customTreeUri)
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_recording)) },
            text = { Text(stringResource(R.string.delete_recording_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            repository.delete(item.uri)
                            preferences.removeFavoriteRecording(item.uri)
                            refreshKey++
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
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
                        renameTarget = null
                        scope.launch {
                            repository.rename(item.uri, renameText.trim())
                            refreshKey++
                        }
                    },
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val filtered = recordings
        .filter { it.displayName.contains(query, ignoreCase = true) }
        .filter { !favoritesOnly || it.uri in favorites }
        .let { list ->
            when (sort) {
                RecordingSort.Newest -> list.sortedByDescending { it.dateAddedMillis }
                RecordingSort.Oldest -> list.sortedBy { it.dateAddedMillis }
                RecordingSort.Largest -> list.sortedByDescending { it.sizeBytes }
            }
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
                            stringResource(R.string.recordings_tab),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                gridMode = !gridMode
                            },
                        ) {
                            Icon(
                                imageVector = if (gridMode) Icons.Default.List else Icons.Default.GridView,
                                contentDescription = stringResource(
                                    if (gridMode) R.string.list_view else R.string.grid_view,
                                ),
                            )
                        }
                        IconButton(
                            onClick = {
                                sort = when (sort) {
                                    RecordingSort.Newest -> RecordingSort.Oldest
                                    RecordingSort.Oldest -> RecordingSort.Largest
                                    RecordingSort.Largest -> RecordingSort.Newest
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = stringResource(R.string.sort),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    label = { Text(stringResource(R.string.search_recordings)) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !favoritesOnly,
                        onClick = { favoritesOnly = false },
                        label = { Text(stringResource(R.string.all_recordings)) },
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = true },
                        leadingIcon = {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                        },
                        label = { Text(stringResource(R.string.favorites)) },
                    )
                }

                if (filtered.isEmpty()) {
                    EmptyLibrary(
                        favoritesOnly = favoritesOnly,
                        onGoToCapture = onGoToCapture,
                    )
                } else if (gridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { it.uri },
                        ) { item ->
                            RecordingGridCard(
                                item = item,
                                favorite = item.uri in favorites,
                                onFavorite = {
                                    scope.launch {
                                        preferences.toggleFavoriteRecording(item.uri)
                                    }
                                },
                                onOpen = { onOpenDetails(item.uri) },
                                onPlay = { onPlayRecording(item.uri) },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { it.uri },
                        ) { item ->
                            RecordingListCard(
                                item = item,
                                favorite = item.uri in favorites,
                                onFavorite = {
                                    scope.launch {
                                        preferences.toggleFavoriteRecording(item.uri)
                                    }
                                },
                                onPlay = { onPlayRecording(item.uri) },
                                onShare = { shareVideo(context, item.uri) },
                                onRename = {
                                    renameText = item.displayName.removeSuffix(".mp4")
                                    renameTarget = item
                                },
                                onDelete = { deleteTarget = item },
                                onDetails = { onOpenDetails(item.uri) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    favoritesOnly: Boolean,
    onGoToCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (favoritesOnly) Icons.Default.FavoriteBorder else Icons.Default.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                if (favoritesOnly) R.string.no_favorites_yet else R.string.no_recordings_yet,
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(
                if (favoritesOnly) R.string.no_favorites_body else R.string.no_recordings_body,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        if (!favoritesOnly) {
            Button(onClick = onGoToCapture) {
                Text(stringResource(R.string.start_recording))
            }
        }
    }
}

@Composable
private fun RecordingGridCard(
    item: RecordingItem,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val thumbnail = rememberVideoThumbnail(item.uri)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onPlay),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.shapes.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.62f),
                    ),
                ) {
                    Text(
                        text = formatDuration(item.durationMillis),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites,
                        ),
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingListCard(
    item: RecordingItem,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    val thumbnail = rememberVideoThumbnail(item.uri)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onPlay),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatDuration(item.durationMillis) + " • " + formatSize(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT,
                        ).format(Date(item.dateAddedMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                }
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Default.DriveFileRenameOutline,
                        contentDescription = stringResource(R.string.rename),
                    )
                }
                IconButton(onClick = onDetails) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.details))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberVideoThumbnail(uriString: String): ImageBitmap? {
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
                        Size(640, 360),
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

internal fun openVideo(context: android.content.Context, uriString: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uriString), "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

internal fun shareVideo(context: android.content.Context, uriString: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share)),
        )
    }
}

internal fun formatDuration(durationMillis: Long): String {
    val total = durationMillis / 1000L
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / 1024f / 1024f
    return if (mb >= 1024f) {
        ((mb / 1024f * 10).roundToInt() / 10f).toString() + " GB"
    } else {
        mb.roundToInt().toString() + " MB"
    }
}
