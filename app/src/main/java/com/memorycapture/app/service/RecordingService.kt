package com.memorycapture.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.memorycapture.app.MainActivity
import com.memorycapture.app.data.preferences.AudioMode
import com.memorycapture.app.data.preferences.InstantReplayDuration
import com.memorycapture.app.data.preferences.RecordingFrameRate
import com.memorycapture.app.data.preferences.RecordingQuality
import com.memorycapture.app.data.preferences.VideoBitratePreset
import com.memorycapture.app.data.recordings.RecordingHighlightRepository
import com.memorycapture.app.R
import com.memorycapture.app.projection.MediaProjectionController
import com.memorycapture.app.recording.GuardianThermalLevel
import com.memorycapture.app.recording.LongSessionWatchdog
import com.memorycapture.app.recording.LongSessionWatchdogInput
import com.memorycapture.app.recording.RecordingError
import com.memorycapture.app.recording.RecordingGuardianStatus
import com.memorycapture.app.recording.RecordingGuardianStore
import com.memorycapture.app.recording.RecordingRecoveryManager
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingStorageSpaceResolver
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecording
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.recording.ScreenRecorderEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RecordingService : Service() {
    private lateinit var projectionController: MediaProjectionController
    private lateinit var recorderEngine: ScreenRecorderEngine
    private lateinit var highlightRepository: RecordingHighlightRepository
    private lateinit var recoveryManager: RecordingRecoveryManager
    private lateinit var storageSpaceResolver: RecordingStorageSpaceResolver
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private val guardianHandler = Handler(Looper.getMainLooper())
    private var floatingControls: FloatingRecordingControls? = null
    private var replayDurationSeconds = InstantReplayDuration.Seconds60.seconds
    private val screenshotInFlight = AtomicBoolean(false)
    private val replaySaveInFlight = AtomicBoolean(false)
    private val maintenanceInFlight = AtomicBoolean(false)
    private val longSessionWatchdog = LongSessionWatchdog()
    @Volatile
    private var recoveryCheckpointEnabled = false
    @Volatile
    private var recoveryCheckpointInFlight = false
    private var recordingTreeUri: String? = null
    @Volatile
    private var lastMaintenanceElapsedMs = 0L
    private var guardianStopInProgress = false
    @Volatile
    private var finalizationInFlight = false
    @Volatile
    private var intentionalStop = false

    override fun onCreate() {
        super.onCreate()
        projectionController = MediaProjectionController(this)
        recorderEngine = ScreenRecorderEngine(this)
        highlightRepository = RecordingHighlightRepository(applicationContext)
        recoveryManager = RecordingRecoveryManager(applicationContext)
        storageSpaceResolver = RecordingStorageSpaceResolver(applicationContext)
        floatingControls = FloatingRecordingControls(
            context = this,
            onPauseResume = {
                when (RecordingStateStore.state.value) {
                    is RecordingState.Paused -> resumeProjectionSession()
                    is RecordingState.Recording -> pauseProjectionSession()
                    else -> Unit
                }
            },
            onScreenshot = {
                if (
                    RecordingStateStore.state.value is RecordingState.Recording &&
                    !finalizationInFlight &&
                    screenshotInFlight.compareAndSet(false, true)
                ) {
                    thread(
                        start = true,
                        name = "MemoryCapture-ScreenshotAction",
                    ) {
                        try {
                            recorderEngine.captureScreenshot()
                        } finally {
                            screenshotInFlight.set(false)
                        }
                    }
                }
            },
            onHighlight = {
                if (RecordingStateStore.state.value is RecordingState.Recording) {
                    RecordingSessionStore.markHighlight()
                }
            },
            onSaveReplay = {
                if (
                    RecordingStateStore.state.value is RecordingState.Recording &&
                    !finalizationInFlight &&
                    replaySaveInFlight.compareAndSet(false, true)
                ) {
                    thread(
                        start = true,
                        name = "MemoryCapture-InstantReplay",
                    ) {
                        try {
                            recorderEngine.saveInstantReplay(replayDurationSeconds)
                        } finally {
                            replaySaveInFlight.set(false)
                        }
                    }
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

        recordingTreeUri = intent.getStringExtra(EXTRA_STORAGE_TREE_URI)

        replayDurationSeconds = runCatching {
            InstantReplayDuration.valueOf(
                intent.getStringExtra(EXTRA_INSTANT_REPLAY_DURATION)
                    ?: InstantReplayDuration.Seconds60.name,
            ).seconds
        }.getOrDefault(InstantReplayDuration.Seconds60.seconds)

        val voipCaptureAssistEnabled = intent.getBooleanExtra(
            EXTRA_VOIP_CAPTURE_ASSIST_ENABLED,
            true,
        )

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
                voipCaptureAssistEnabled = voipCaptureAssistEnabled,
                quality = recordingQuality,
                frameRate = recordingFrameRate,
                bitratePreset = videoBitratePreset,
            )
        }.onSuccess {
            RecordingSessionStore.markStarted()
            recoveryManager.markSessionActive()
            recoveryCheckpointEnabled = true
            guardianStopInProgress = false
            longSessionWatchdog.reset()
            lastMaintenanceElapsedMs = SystemClock.elapsedRealtime()
            scheduleRecoveryCheckpoint()
            scheduleGuardian()
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
        if (
            finalizationInFlight ||
            (state !is RecordingState.Recording && state !is RecordingState.Paused)
        ) {
            return
        }

        RecordingStateStore.transition(RecordingState.Stopping)
        RecordingStateStore.transition(RecordingState.Processing)

        recoveryCheckpointEnabled = false
        cancelRecoveryCheckpoint()
        cancelGuardian()
        floatingControls?.hide()
        intentionalStop = true
        finalizationInFlight = true

        val highlights = RecordingSessionStore.snapshotHighlights()

        thread(
            start = true,
            name = "MemoryCapture-FinalizeRecording",
        ) {
            val saved = runCatching {
                recorderEngine.stopAndSave()
            }.getOrNull()
            val recovered = if (saved == null) {
                runCatching {
                    recoveryManager.recoverIfNeeded()
                }.getOrNull()
            } else {
                null
            }

            recoveryHandler.post {
                projectionController.stop()

                when {
                    saved != null -> {
                        publishSavedRecording(saved)
                        highlightRepository.save(
                            recordingUri = saved.uri?.toString(),
                            displayName = saved.displayName,
                            highlightsMillis = highlights,
                        )
                        recoveryManager.markSessionClosed()
                        RecordingStateStore.transition(RecordingState.Completed)
                    }

                    recovered != null -> {
                        SavedRecordingStore.setSaved(
                            displayName = recovered.displayName,
                            location = recovered.locationLabel,
                        )
                        RecordingStateStore.transition(RecordingState.Completed)
                    }

                    else -> {
                        RecordingStateStore.forceError(RecordingError.MuxerFailure)
                    }
                }

                finalizationInFlight = false
                RecordingSessionStore.clear()
                stopForegroundAndSelf()
            }
        }
    }

    private fun handleProjectionStopped() {
        if (intentionalStop || finalizationInFlight) return

        val current = RecordingStateStore.state.value
        if (current !is RecordingState.Recording && current !is RecordingState.Paused) {
            return
        }

        RecordingStateStore.transition(RecordingState.Stopping)
        RecordingStateStore.transition(RecordingState.Processing)

        recoveryCheckpointEnabled = false
        cancelRecoveryCheckpoint()
        cancelGuardian()
        floatingControls?.hide()
        intentionalStop = true
        finalizationInFlight = true

        val highlights = RecordingSessionStore.snapshotHighlights()

        thread(
            start = true,
            name = "MemoryCapture-FinalizeInterrupted",
        ) {
            val saved = runCatching {
                recorderEngine.stopAndSave()
            }.getOrNull()
            val recovered = if (saved == null) {
                runCatching {
                    recoveryManager.recoverIfNeeded()
                }.getOrNull()
            } else {
                null
            }

            recoveryHandler.post {
                when {
                    saved != null -> {
                        publishSavedRecording(saved)
                        highlightRepository.save(
                            recordingUri = saved.uri?.toString(),
                            displayName = saved.displayName,
                            highlightsMillis = highlights,
                        )
                        recoveryManager.markSessionClosed()
                        RecordingStateStore.transition(RecordingState.Completed)
                    }

                    recovered != null -> {
                        SavedRecordingStore.setSaved(
                            displayName = recovered.displayName,
                            location = recovered.locationLabel,
                        )
                        RecordingStateStore.transition(RecordingState.Completed)
                    }

                    else -> {
                        RecordingStateStore.forceError(
                            RecordingError.RecordingInterrupted,
                        )
                    }
                }

                finalizationInFlight = false
                RecordingSessionStore.clear()
                stopForegroundAndSelf()
            }
        }
    }

    private fun publishSavedRecording(saved: SavedRecording) {
        SavedRecordingStore.setSaved(
            displayName = saved.displayName,
            location = saved.locationLabel,
        )
    }

    private val recoveryRunnable: Runnable = object : Runnable {
        override fun run() {
            if (
                !recoveryCheckpointEnabled ||
                !recorderEngine.isActive() ||
                recoveryCheckpointInFlight
            ) {
                return
            }

            recoveryCheckpointInFlight = true

            thread(
                start = true,
                name = "MemoryCapture-RecoveryCheckpoint",
            ) {
                try {
                    recoveryManager.repairActiveSessionArtifacts()

                    val written = recorderEngine.writeRecoveryCheckpoint(
                        targetFile = recoveryManager.checkpointTempFile,
                        durationSeconds = RECOVERY_WINDOW_SECONDS,
                    )

                    if (written && recoveryCheckpointEnabled) {
                        recoveryManager.commitCheckpoint()
                    } else {
                        runCatching { recoveryManager.checkpointTempFile.delete() }
                    }
                } finally {
                    recoveryCheckpointInFlight = false

                    if (
                        recoveryCheckpointEnabled &&
                        recorderEngine.isActive()
                    ) {
                        recoveryHandler.postDelayed(
                            recoveryRunnable,
                            RECOVERY_INTERVAL_MS,
                        )
                    }
                }
            }
        }
    }

    private fun scheduleRecoveryCheckpoint() {
        recoveryHandler.removeCallbacks(recoveryRunnable)
        if (
            recoveryCheckpointEnabled &&
            !recoveryCheckpointInFlight
        ) {
            recoveryHandler.postDelayed(
                recoveryRunnable,
                RECOVERY_INTERVAL_MS,
            )
        }
    }

    private fun cancelRecoveryCheckpoint() {
        recoveryHandler.removeCallbacks(recoveryRunnable)
    }

    private val guardianRunnable = object : Runnable {
        override fun run() {
            if (!recorderEngine.isActive()) {
                RecordingGuardianStore.clear()
                return
            }

            val bytesPerSecond = recorderEngine.estimatedOutputBytesPerSecond()
            val destinationAvailableBytes =
                storageSpaceResolver.recordingDestinationAvailableBytes(recordingTreeUri)
            val workingAvailableBytes =
                storageSpaceResolver.workingStorageAvailableBytes()

            val remainingSeconds =
                destinationAvailableBytes
                    ?.takeIf { bytesPerSecond > 0L }
                    ?.let { (it / bytesPerSecond).coerceAtLeast(0L) }

            val thermalLevel = currentThermalLevel()
            val audioHealth = recorderEngine.audioHealth()
            val voipStatus = recorderEngine.refreshVoipCaptureStatus()
            val destinationWarning =
                destinationAvailableBytes?.let { available ->
                    available <= STORAGE_WARNING_BYTES ||
                        (remainingSeconds != null &&
                            remainingSeconds <= STORAGE_WARNING_SECONDS)
                } ?: false

            val workspaceWarningThreshold = maxOf(
                WORKSPACE_WARNING_BYTES,
                bytesPerSecond * WORKSPACE_WARNING_SECONDS,
            )
            val workspaceStopThreshold = maxOf(
                WORKSPACE_STOP_BYTES,
                bytesPerSecond * WORKSPACE_STOP_SECONDS,
            )

            val workingStorageWarning =
                workingAvailableBytes?.let { it <= workspaceWarningThreshold }
                    ?: false

            val thermalWarning =
                thermalLevel == GuardianThermalLevel.Hot ||
                    thermalLevel == GuardianThermalLevel.Critical

            RecordingGuardianStore.update(
                RecordingGuardianStatus(
                    destinationAvailableBytes = destinationAvailableBytes,
                    workingAvailableBytes = workingAvailableBytes,
                    estimatedRemainingSeconds = remainingSeconds,
                    destinationSpaceKnown = destinationAvailableBytes != null,
                    thermalLevel = thermalLevel,
                    audioHealth = audioHealth,
                    voipStatus = voipStatus,
                    storageWarning = destinationWarning,
                    workingStorageWarning = workingStorageWarning,
                    thermalWarning = thermalWarning,
                ),
            )

            val destinationDanger =
                destinationAvailableBytes?.let { available ->
                    available <= STORAGE_STOP_BYTES ||
                        (remainingSeconds != null &&
                            remainingSeconds <= STORAGE_STOP_SECONDS)
                } ?: false

            val workspaceDanger =
                workingAvailableBytes?.let { it <= workspaceStopThreshold }
                    ?: false

            val thermalDanger = currentThermalStatusRaw() >= THERMAL_EMERGENCY_STATUS
            val nowElapsedMs = SystemClock.elapsedRealtime()
            val watchdogDecision = longSessionWatchdog.evaluate(
                LongSessionWatchdogInput(
                    fatalRuntimeFailure = recorderEngine.hasFatalRuntimeFailure(),
                    videoDrainStalled = recorderEngine.isVideoDrainStalled(
                        nowElapsedMs = nowElapsedMs,
                        thresholdMs = VIDEO_DRAIN_STALL_THRESHOLD_MS,
                    ),
                    audioCaptureStalled = recorderEngine.isAudioCaptureStalled(
                        nowElapsedMs = nowElapsedMs,
                        thresholdMs = AUDIO_STALL_THRESHOLD_MS,
                    ),
                ),
            )

            if (watchdogDecision.degradeAudio) {
                recorderEngine.degradeStalledAudio()
            }

            scheduleLongSessionMaintenance(nowElapsedMs)

            if (
                !guardianStopInProgress &&
                (
                    destinationDanger ||
                        workspaceDanger ||
                        thermalDanger ||
                        watchdogDecision.stopRecording
                    ) &&
                (
                    RecordingStateStore.state.value is RecordingState.Recording ||
                        RecordingStateStore.state.value is RecordingState.Paused
                )
            ) {
                guardianStopInProgress = true
                stopProjectionSession()
                return
            }

            guardianHandler.postDelayed(this, GUARDIAN_INTERVAL_MS)
        }
    }

    private fun scheduleGuardian() {
        guardianHandler.removeCallbacks(guardianRunnable)
        guardianHandler.post(guardianRunnable)
    }

    private fun cancelGuardian() {
        guardianHandler.removeCallbacks(guardianRunnable)
        longSessionWatchdog.reset()
    }

    private fun scheduleLongSessionMaintenance(nowElapsedMs: Long) {
        if (
            finalizationInFlight ||
            nowElapsedMs - lastMaintenanceElapsedMs < MAINTENANCE_INTERVAL_MS ||
            !maintenanceInFlight.compareAndSet(false, true)
        ) {
            return
        }

        thread(
            start = true,
            name = "MemoryCapture-LongSessionMaintenance",
        ) {
            try {
                recorderEngine.performLongSessionMaintenance(
                    cleanReplayExports = !replaySaveInFlight.get(),
                )
            } finally {
                lastMaintenanceElapsedMs = SystemClock.elapsedRealtime()
                maintenanceInFlight.set(false)
            }
        }
    }

    private fun currentThermalStatusRaw(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(PowerManager::class.java).currentThermalStatus
        } else {
            0
        }

    private fun currentThermalLevel(): GuardianThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return GuardianThermalLevel.Normal
        }

        return when (currentThermalStatusRaw()) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> GuardianThermalLevel.Normal
            PowerManager.THERMAL_STATUS_MODERATE -> GuardianThermalLevel.Warm
            PowerManager.THERMAL_STATUS_SEVERE -> GuardianThermalLevel.Hot
            else -> GuardianThermalLevel.Critical
        }
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
        recoveryCheckpointEnabled = false
        cancelRecoveryCheckpoint()
        cancelGuardian()
        RecordingGuardianStore.clear()
        floatingControls?.hide()

        if (recorderEngine.isActive() && !finalizationInFlight) {
            intentionalStop = true
            recorderEngine.abort()
        }

        RecordingSessionStore.clear()
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
        const val EXTRA_INSTANT_REPLAY_DURATION = "instant_replay_duration"
        const val EXTRA_VOIP_CAPTURE_ASSIST_ENABLED = "voip_capture_assist_enabled"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_INTERVAL_MS = 30_000L
        private const val RECOVERY_WINDOW_SECONDS = 60
        private const val GUARDIAN_INTERVAL_MS = 5_000L
        private const val VIDEO_DRAIN_STALL_THRESHOLD_MS = 20_000L
        private const val AUDIO_STALL_THRESHOLD_MS = 15_000L
        private const val MAINTENANCE_INTERVAL_MS = 60_000L
        private const val STORAGE_WARNING_BYTES = 500L * 1024L * 1024L
        private const val STORAGE_STOP_BYTES = 120L * 1024L * 1024L
        private const val STORAGE_WARNING_SECONDS = 180L
        private const val STORAGE_STOP_SECONDS = 45L
        private const val WORKSPACE_WARNING_BYTES = 600L * 1024L * 1024L
        private const val WORKSPACE_STOP_BYTES = 256L * 1024L * 1024L
        private const val WORKSPACE_WARNING_SECONDS = 360L
        private const val WORKSPACE_STOP_SECONDS = 180L
        private const val THERMAL_EMERGENCY_STATUS = 5
    }
}
