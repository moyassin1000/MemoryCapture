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
    private var intentionalStop = false

    override fun onCreate() {
        super.onCreate()
        projectionController = MediaProjectionController(this)
        recorderEngine = ScreenRecorderEngine(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProjectionSession(intent)
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
            )
        }.onSuccess {
            RecordingSessionStore.markStarted()
            RecordingStateStore.transition(RecordingState.Recording)
        }.onFailure {
            recorderEngine.abort()
            RecordingSessionStore.clear()
            intentionalStop = true
            projectionController.stop()
            RecordingStateStore.forceError(RecordingError.EncoderUnavailable)
            stopForegroundAndSelf()
        }
    }

    private fun stopProjectionSession() {
        val state = RecordingStateStore.state.value
        if (state !is RecordingState.Recording && state !is RecordingState.Paused) return

        RecordingStateStore.transition(RecordingState.Stopping)
        RecordingStateStore.transition(RecordingState.Processing)

        val saved = recorderEngine.stopAndSave()
        RecordingSessionStore.clear()
        intentionalStop = true
        projectionController.stop()

        if (saved != null) {
            publishSavedRecording(saved)
            RecordingStateStore.transition(RecordingState.Completed)
        } else {
            RecordingStateStore.forceError(RecordingError.MuxerFailure)
        }

        stopForegroundAndSelf()
    }

    private fun handleProjectionStopped() {
        if (intentionalStop) return

        val current = RecordingStateStore.state.value
        if (current is RecordingState.Recording || current is RecordingState.Paused) {
            RecordingStateStore.transition(RecordingState.Stopping)
            RecordingStateStore.transition(RecordingState.Processing)

            val saved = recorderEngine.stopAndSave()
            RecordingSessionStore.clear()
            if (saved != null) {
                publishSavedRecording(saved)
                RecordingStateStore.transition(RecordingState.Completed)
            } else {
                RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
            }
        }

        intentionalStop = true
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
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()

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
        if (recorderEngine.isActive()) {
            val saved = recorderEngine.stopAndSave()
            if (saved != null) publishSavedRecording(saved)
        }
        RecordingSessionStore.clear()
        intentionalStop = true
        projectionController.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.memorycapture.app.action.START"
        const val ACTION_STOP = "com.memorycapture.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STORAGE_TREE_URI = "storage_tree_uri"
        const val EXTRA_STORAGE_LABEL = "storage_label"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
    }
}
