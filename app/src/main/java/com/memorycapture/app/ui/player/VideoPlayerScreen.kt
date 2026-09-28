package com.memorycapture.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.memorycapture.app.R
import com.memorycapture.app.ui.components.PremiumBackground
import com.memorycapture.app.ui.recordings.shareVideo
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    uriString: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val inPip by PlayerUiModeStore.inPictureInPicture.collectAsStateWithLifecycle()
    val uri = remember(uriString) { Uri.parse(uriString) }
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
    var fullscreen by remember { mutableStateOf(false) }
    var playerSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            activity?.let { restoreSystemBars(it) }
            player.release()
        }
    }

    LaunchedEffect(fullscreen, activity) {
        activity?.let {
            if (fullscreen) hideSystemBars(it) else restoreSystemBars(it)
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

    LaunchedEffect(gestureText) {
        if (gestureText != null) {
            delay(700)
            gestureText = null
        }
    }

    BackHandler {
        if (fullscreen) {
            fullscreen = false
        } else {
            onBack()
        }
    }

    PremiumBackground {
        Scaffold(
            containerColor = if (fullscreen || inPip) Color.Black else Color.Transparent,
            topBar = {
                if (!fullscreen && !inPip) {
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
                            IconButton(
                                onClick = {
                                    activity?.let { enterPip(it) }
                                },
                            ) {
                                Icon(
                                    Icons.Default.PictureInPicture,
                                    contentDescription = stringResource(R.string.picture_in_picture),
                                )
                            }
                            IconButton(onClick = { fullscreen = true }) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = stringResource(R.string.fullscreen),
                                )
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fullscreen || inPip) androidx.compose.foundation.layout.PaddingValues() else padding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                        .onSizeChanged { playerSize = it }
                        .pointerInput(durationMs, playerSize, fullscreen) {
                            var start = Offset.Zero
                            var total = Offset.Zero
                            var startPosition = 0L
                            var startBrightness = 0.5f
                            var startVolume = 0

                            detectDragGestures(
                                onDragStart = { offset ->
                                    start = offset
                                    total = Offset.Zero
                                    startPosition = player.currentPosition
                                    startBrightness = activity
                                        ?.window
                                        ?.attributes
                                        ?.screenBrightness
                                        ?.takeIf { it >= 0f }
                                        ?: 0.5f
                                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                },
                                onDragEnd = {
                                    gestureText = null
                                },
                                onDragCancel = {
                                    gestureText = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    total += amount

                                    if (abs(total.x) > abs(total.y)) {
                                        if (playerSize.width > 0 && durationMs > 0L) {
                                            val fraction = total.x / playerSize.width.toFloat()
                                            val delta = (fraction * 120_000L).toLong()
                                            val target = (startPosition + delta)
                                                .coerceIn(0L, durationMs)
                                            player.seekTo(target)
                                            gestureText = formatPlayerTime(target)
                                        }
                                    } else if (playerSize.height > 0) {
                                        val fraction = (-total.y / playerSize.height.toFloat())
                                            .coerceIn(-1f, 1f)

                                        if (start.x < playerSize.width / 2f) {
                                            activity?.let { host ->
                                                val brightness = (startBrightness + fraction)
                                                    .coerceIn(0.05f, 1f)
                                                host.window.attributes = host.window.attributes.apply {
                                                    screenBrightness = brightness
                                                }
                                                gestureText = stringResource(
                                                    R.string.brightness_percent,
                                                    (brightness * 100).toInt(),
                                                )
                                            }
                                        } else {
                                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val volume = (startVolume + (fraction * max).toInt())
                                                .coerceIn(0, max)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                volume,
                                                0,
                                            )
                                            val percent = if (max > 0) volume * 100 / max else 0
                                            gestureText = stringResource(
                                                R.string.volume_percent,
                                                percent,
                                            )
                                        }
                                    }
                                },
                            )
                        },
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

                    gestureText?.let { overlay ->
                        Text(
                            text = overlay,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.58f),
                                    MaterialTheme.shapes.medium,
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }

                    if (fullscreen && !inPip) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                        ) {
                            IconButton(onClick = { activity?.let { enterPip(it) } }) {
                                Icon(
                                    Icons.Default.PictureInPicture,
                                    contentDescription = stringResource(R.string.picture_in_picture),
                                    tint = Color.White,
                                )
                            }
                            IconButton(onClick = { fullscreen = false }) {
                                Icon(
                                    Icons.Default.FullscreenExit,
                                    contentDescription = stringResource(R.string.exit_fullscreen),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                if (!inPip) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
}

private fun enterPip(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
}

private fun hideSystemBars(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(
        activity.window,
        activity.window.decorView,
    ).hide(WindowInsetsCompat.Type.systemBars())
}

private fun restoreSystemBars(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowInsetsControllerCompat(
        activity.window,
        activity.window.decorView,
    ).show(WindowInsetsCompat.Type.systemBars())
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
