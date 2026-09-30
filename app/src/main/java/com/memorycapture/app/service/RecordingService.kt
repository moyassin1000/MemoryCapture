package com.memorycapture.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.memorycapture.app.MainActivity
import com.memorycapture.app.data.preferences.AudioMode
import com.memorycapture.app.data.preferences.RecordingFrameRate
import com.memorycapture.app.data.preferences.RecordingQuality
import com.memorycapture.app.data.preferences.VideoBitratePreset
import com.memorycapture.app.data.recordings.RecordingHighlightRepository
import com.memorycapture.app.R
import com.memorycapture.app.projection.MediaProjectionController
import com.memorycapture.app.recording.RecordingError
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecording
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.recording.ScreenRecorderEngine

class RecordingService : Service() {
    private lateinit var projectionController: MediaProjectionController
    private lateinit var recorderEngine: ScreenRecorderEngine
    private lateinit var highlightRepository: RecordingHighlightRepository
    private var floatingControls: FloatingRecordingControls? = null
    private var intentionalStop = false

    override fun onCreate() {
        super.onCreate()
        projectionController = MediaProjectionController(this)
        recorderEngine = ScreenRecorderEngine(this)
        highlightRepository = RecordingHighlightRepository(applicationContext)
        floatingControls = FloatingRecordingControls(
            context = this,
            onPauseResume = {
                when (RecordingStateStore.state.value) {
                    is RecordingState.Paused -> resumeProjectionSession()
                    is RecordingState.Recording -> pauseProjectionSession()
                    else -> Unit
                }
            },
            onHighlight = {
                if (RecordingStateStore.state.value is RecordingState.Recording) {
                    RecordingSessionStore.markHighlight()
                }
            },
            onStop = ::stopProjectionSession,
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProjectionSession(intent)
            ACTION_PAUSE -> pauseProjectionSession()
            ACTION_RESUME -> resumeProjectionSession()
            ACTION_STOP -> stopProjectionSession()
        }
        return START_NOT_STICKY
    }

    private fun startProjectionSession(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Int.MIN_VALUE || resultData == null) {
            RecordingStateStore.forceError(RecordingError.MediaProjectionDenied)
            stopSelf()
            return
        }

        val audioMode = runCatching {
            AudioMode.valueOf(
                intent.getStringExtra(EXTRA_AUDIO_MODE) ?: AudioMode.None.name,
            )
        }.getOrDefault(AudioMode.None)

        val recordingQuality = runCatching {
            RecordingQuality.valueOf(
                intent.getStringExtra(EXTRA_RECORDING_QUALITY)
                    ?: RecordingQuality.P1080.name,
            )
        }.getOrDefault(RecordingQuality.P1080)

        val recordingFrameRate = runCatching {
            RecordingFrameRate.valueOf(
                intent.getStringExtra(EXTRA_RECORDING_FRAME_RATE)
                    ?: RecordingFrameRate.Fps30.name,
            )
        }.getOrDefault(RecordingFrameRate.Fps30)

        val videoBitratePreset = runCatching {
            VideoBitratePreset.valueOf(
                intent.getStringExtra(EXTRA_VIDEO_BITRATE_PRESET)
                    ?: VideoBitratePreset.Balanced.name,
            )
        }.getOrDefault(VideoBitratePreset.Balanced)

        startAsForeground(audioMode)
        intentionalStop = false

        runCatching {
            val projection = projectionController.start(resultCode, resultData) {
                handleProjectionStopped()
            }
            recorderEngine.start(
                projection = projection,
                customTreeUri = intent.getStringExtra(EXTRA_STORAGE_TREE_URI),
                customStorageLabel = intent.getStringExtra(EXTRA_STORAGE_LABEL),
                audioMode = audioMode,
                preferredMicDeviceId = intent.getIntExtra(
                    EXTRA_MICROPHONE_DEVICE_ID,
                    -1,
                ),
                quality = recordingQuality,
                frameRate = recordingFrameRate,
                bitratePreset = videoBitratePreset,
            )
        }.onSuccess {
            RecordingSessionStore.markStarted()
            RecordingStateStore.transition(RecordingState.Recording)
            floatingControls?.show()
            floatingControls?.updatePaused(false)
        }.onFailure { error ->
            recorderEngine.abort()
            RecordingSessionStore.clear()
            intentionalStop = true
            projectionController.stop()

            val reason = when {
                error is SecurityException -> RecordingError.MicrophoneDenied
                error.message?.contains("audio", ignoreCase = true) == true ->
                    RecordingError.AudioCaptureUnsupported
                else -> RecordingError.EncoderUnavailable
            }

            RecordingStateStore.forceError(reason)
            stopForegroundAndSelf()
        }
    }

    private fun pauseProjectionSession() {
        if (RecordingStateStore.state.value !is RecordingState.Recording) return

        runCatching { recorderEngine.pause() }
            .onSuccess {
                RecordingSessionStore.markPaused()
                RecordingStateStore.transition(RecordingState.Paused)
                updateForegroundNotification(paused = true)
                floatingControls?.updatePaused(true)
            }
            .onFailure {
                RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
                stopForegroundAndSelf()
            }
    }

    private fun resumeProjectionSession() {
        if (RecordingStateStore.state.value !is RecordingState.Paused) return

        runCatching { recorderEngine.resume() }
            .onSuccess {
                RecordingSessionStore.markResumed()
                RecordingStateStore.transition(RecordingState.Recording)
                updateForegroundNotification(paused = false)
                floatingControls?.updatePaused(false)
            }
            .onFailure {
                RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
                stopForegroundAndSelf()
            }
    }

    private fun stopProjectionSession() {
        val state = RecordingStateStore.state.value
        if (state !is RecordingState.Recording && state !is RecordingState.Paused) return

        RecordingStateStore.transition(RecordingState.Stopping)
        RecordingStateStore.transition(RecordingState.Processing)

        val highlights = RecordingSessionStore.snapshotHighlights()
        val saved = recorderEngine.stopAndSave()
        intentionalStop = true
        projectionController.stop()

        if (saved != null) {
            publishSavedRecording(saved)
            highlightRepository.save(
                recordingUri = saved.uri?.toString(),
                displayName = saved.displayName,
                highlightsMillis = highlights,
            )
            RecordingStateStore.transition(RecordingState.Completed)
        } else {
            RecordingStateStore.forceError(RecordingError.MuxerFailure)
        }

        RecordingSessionStore.clear()
        floatingControls?.hide()
        stopForegroundAndSelf()
    }

    private fun handleProjectionStopped() {
        if (intentionalStop) return

        val current = RecordingStateStore.state.value
        if (current is RecordingState.Recording || current is RecordingState.Paused) {
            RecordingStateStore.transition(RecordingState.Stopping)
            RecordingStateStore.transition(RecordingState.Processing)

            val highlights = RecordingSessionStore.snapshotHighlights()
            val saved = recorderEngine.stopAndSave()
            if (saved != null) {
                publishSavedRecording(saved)
                highlightRepository.save(
                    recordingUri = saved.uri?.toString(),
                    displayName = saved.displayName,
                    highlightsMillis = highlights,
                )
                RecordingStateStore.transition(RecordingState.Completed)
            } else {
                RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
            }
        }

        RecordingSessionStore.clear()
        intentionalStop = true
        floatingControls?.hide()
        stopForegroundAndSelf()
    }

    private fun publishSavedRecording(saved: SavedRecording) {
        SavedRecordingStore.setSaved(
            displayName = saved.displayName,
            location = saved.locationLabel,
        )
    }

    private fun startAsForeground(audioMode: AudioMode) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = buildNotification(
            openApp = openApp,
            paused = false,
        )

        val serviceType =
            if (
                audioMode == AudioMode.Microphone ||
                audioMode == AudioMode.DeviceAndMic
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType,
        )
    }

    private fun updateForegroundNotification(paused: Boolean) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(openApp = openApp, paused = paused),
        )
    }

    private fun buildNotification(
        openApp: PendingIntent,
        paused: Boolean,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(
            getString(
                if (paused) R.string.notification_title_paused
                else R.string.notification_title,
            ),
        )
        .setContentText(
            getString(
                if (paused) R.string.notification_text_paused
                else R.string.notification_text,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp)
        .addAction(
            0,
            getString(
                if (paused) R.string.notification_resume
                else R.string.notification_pause,
            ),
            PendingIntent.getService(
                this,
                2,
                Intent(this, RecordingService::class.java).setAction(
                    if (paused) ACTION_RESUME else ACTION_PAUSE,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            getString(R.string.notification_stop),
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            },
        )
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        floatingControls?.hide()
        if (recorderEngine.isActive()) {
            val highlights = RecordingSessionStore.snapshotHighlights()
            val saved = recorderEngine.stopAndSave()
            if (saved != null) {
                publishSavedRecording(saved)
                highlightRepository.save(
                    recordingUri = saved.uri?.toString(),
                    displayName = saved.displayName,
                    highlightsMillis = highlights,
                )
            }
        }
        RecordingSessionStore.clear()
        intentionalStop = true
        projectionController.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.memorycapture.app.action.START"
        const val ACTION_PAUSE = "com.memorycapture.app.action.PAUSE"
        const val ACTION_RESUME = "com.memorycapture.app.action.RESUME"
        const val ACTION_STOP = "com.memorycapture.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STORAGE_TREE_URI = "storage_tree_uri"
        const val EXTRA_STORAGE_LABEL = "storage_label"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        const val EXTRA_MICROPHONE_DEVICE_ID = "microphone_device_id"
        const val EXTRA_RECORDING_QUALITY = "recording_quality"
        const val EXTRA_RECORDING_FRAME_RATE = "recording_frame_rate"
        const val EXTRA_VIDEO_BITRATE_PRESET = "video_bitrate_preset"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
    }
}
