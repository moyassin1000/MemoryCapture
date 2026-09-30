package com.memorycapture.app.ui.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.memorycapture.app.R
import com.memorycapture.app.data.recordings.RecordingHighlightRepository
import com.memorycapture.app.ui.components.PremiumBackground
import com.memorycapture.app.ui.recordings.shareVideo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    uriString: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }
    val highlightRepository = remember {
        RecordingHighlightRepository(context.applicationContext)
    }
    val highlights = remember(uriString) {
        highlightRepository.loadByUri(uriString)
    }
    val player = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, scrubbing) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (!scrubbing && durationMs > 0L) {
                sliderPosition = (positionMs.toFloat() / durationMs.toFloat())
                    .coerceIn(0f, 1f)
            }
            delay(250)
        }
    }

    BackHandler(onBack = onBack)

    PremiumBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = { Text(stringResource(R.string.video_player)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { shareVideo(context, uriString) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                useController = false
                                this.player = player
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                        update = { it.player = player },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (highlights.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.highlights),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(highlights.size) { index ->
                                val timestamp = highlights[index]
                                AssistChip(
                                    onClick = {
                                        player.seekTo(timestamp)
                                        player.play()
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.highlight_number_time,
                                                index + 1,
                                                formatPlayerTime(timestamp),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            scrubbing = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0L) {
                                player.seekTo((durationMs * sliderPosition).toLong())
                            }
                            scrubbing = false
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatPlayerTime(positionMs))
                        Text(formatPlayerTime(durationMs))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            },
                        ) {
                            Icon(
                                Icons.Default.FastRewind,
                                contentDescription = stringResource(R.string.rewind_10_seconds),
                            )
                        }

                        Button(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play,
                                ),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                val max = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(max))
                            },
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = stringResource(R.string.forward_10_seconds),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
