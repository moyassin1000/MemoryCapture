package com.memorycapture.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.memorycapture.app.billing.ProBillingManager
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.AudioMode
import com.memorycapture.app.data.preferences.ProAccent
import com.memorycapture.app.data.preferences.RecordingFrameRate
import com.memorycapture.app.data.preferences.RecordingQuality
import com.memorycapture.app.data.preferences.ThemeMode
import com.memorycapture.app.data.preferences.VideoBitratePreset
import com.memorycapture.app.navigation.MemoryCaptureNavHost
import com.memorycapture.app.recording.CountdownStore
import com.memorycapture.app.recording.RecordingError
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.recording.SavedRecordingStore
import com.memorycapture.app.service.RecordingService
import com.memorycapture.app.ui.theme.MemoryCaptureTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val projectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }
    private val preferences by lazy { AppPreferences(applicationContext) }

    private val projectionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                RecordingStateStore.transition(RecordingState.Countdown)
                SavedRecordingStore.clear()

                val countdownEnabled = preferences.countdownEnabled.first()
                val countdownSeconds = preferences.countdownSeconds.first()

                if (countdownEnabled) {
                    for (second in countdownSeconds downTo 1) {
                        CountdownStore.show(second)
                        kotlinx.coroutines.delay(1_000)
                    }
                }

                CountdownStore.clear()
                startRecordingService(result.resultCode, requireNotNull(result.data))
            }
        } else {
            CountdownStore.clear()
            RecordingStateStore.forceError(RecordingError.MediaProjectionDenied)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            lifecycleScope.launch { continueRecordingPermissionFlow() }
        } else {
            RecordingStateStore.forceError(RecordingError.MicrophoneDenied)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        launchProjectionPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        runCatching { ProBillingManager.initialize(applicationContext) }

        setContent {
            val themeMode = preferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.System,
            ).value
            val proAccent = preferences.proAccent.collectAsStateWithLifecycle(
                initialValue = ProAccent.Electric,
            ).value
            val proState = ProBillingManager.state.collectAsStateWithLifecycle().value

            MemoryCaptureTheme(
                themeMode = themeMode,
                proAccent = proAccent,
                proEnabled = proState.isPro,
            ) {
                MemoryCaptureNavHost(
                    onRequestRecording = ::requestRecording,
                    onPauseRecording = ::pauseRecordingService,
                    onResumeRecording = ::resumeRecordingService,
                    onStopRecording = ::stopRecordingService,
                    onExitApp = { finishAffinity() },
                )
            }
        }
    }

    private fun requestRecording() {
        if (!RecordingStateStore.transition(RecordingState.Preparing)) return
        lifecycleScope.launch {
            continueRecordingPermissionFlow()
        }
    }

    private suspend fun continueRecordingPermissionFlow() {
        val audioMode = effectiveAudioMode(preferences.audioMode.first())

        if (
            audioMode != AudioMode.None &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            RecordingStateStore.transition(RecordingState.PermissionRequired)
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            RecordingStateStore.transition(RecordingState.PermissionRequired)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjectionPermission()
        }
    }

    private fun launchProjectionPermission() {
        RecordingStateStore.transition(RecordingState.PermissionRequired)
        projectionPermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private suspend fun startRecordingService(resultCode: Int, resultData: Intent) {
        val storageTreeUri = preferences.storageTreeUri.first()
        val storageLabel = preferences.storageLabel.first()
        val audioMode = effectiveAudioMode(preferences.audioMode.first())
        val microphoneDeviceId = preferences.microphoneDeviceId.first()
        val recordingQuality = preferences.recordingQuality.first()
        val recordingFrameRate = preferences.recordingFrameRate.first()
        val videoBitratePreset = preferences.videoBitratePreset.first()

        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(RecordingService.EXTRA_RESULT_DATA, resultData)
            .putExtra(RecordingService.EXTRA_STORAGE_TREE_URI, storageTreeUri)
            .putExtra(RecordingService.EXTRA_STORAGE_LABEL, storageLabel)
            .putExtra(RecordingService.EXTRA_AUDIO_MODE, audioMode.name)
            .putExtra(RecordingService.EXTRA_MICROPHONE_DEVICE_ID, microphoneDeviceId)
            .putExtra(RecordingService.EXTRA_RECORDING_QUALITY, recordingQuality.name)
            .putExtra(RecordingService.EXTRA_RECORDING_FRAME_RATE, recordingFrameRate.name)
            .putExtra(RecordingService.EXTRA_VIDEO_BITRATE_PRESET, videoBitratePreset.name)

        ContextCompat.startForegroundService(this, intent)
    }

    private fun effectiveAudioMode(mode: AudioMode): AudioMode =
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            (mode == AudioMode.DeviceAudio || mode == AudioMode.DeviceAndMic)
        ) {
            AudioMode.Microphone
        } else {
            mode
        }

    override fun onDestroy() {
        if (isFinishing) runCatching { ProBillingManager.close() }
        super.onDestroy()
    }

    private fun pauseRecordingService() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_PAUSE),
        )
    }

    private fun resumeRecordingService() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_RESUME),
        )
    }

    private fun stopRecordingService() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_STOP),
        )
    }
}
