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
import com.memorycapture.app.R
import com.memorycapture.app.projection.MediaProjectionController
import com.memorycapture.app.recording.RecordingError
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore

class RecordingService : Service() {
    private lateinit var projectionController: MediaProjectionController

    override fun onCreate() {
        super.onCreate()
        projectionController = MediaProjectionController(this)
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

        startAsForeground()
        RecordingStateStore.transition(RecordingState.Countdown)

        runCatching {
            projectionController.start(resultCode, resultData) {
                val state = RecordingStateStore.state.value
                if (state !is RecordingState.Stopping && state !is RecordingState.Completed) {
                    RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
                }
                stopSelf()
            }
        }.onSuccess {
            // Phase 1 intentionally acquires a real MediaProjection token but does not
            // create a VirtualDisplay/encoder yet. That is Phase 2.
            RecordingStateStore.transition(RecordingState.Recording)
        }.onFailure {
            RecordingStateStore.forceError(RecordingError.UnknownError)
            stopSelf()
        }
    }

    private fun stopProjectionSession() {
        if (RecordingStateStore.state.value is RecordingState.Recording ||
            RecordingStateStore.state.value is RecordingState.Paused
        ) {
            RecordingStateStore.transition(RecordingState.Stopping)
        }
        projectionController.stop()
        RecordingStateStore.transition(RecordingState.Completed)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
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

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
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

    override fun onDestroy() {
        projectionController.stop()
        if (RecordingStateStore.state.value is RecordingState.Recording) {
            RecordingStateStore.forceError(RecordingError.RecordingInterrupted)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.memorycapture.app.action.START"
        const val ACTION_STOP = "com.memorycapture.app.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
    }
}
