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
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.ThemeMode
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        launchProjectionPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode = preferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.System,
            ).value

            MemoryCaptureTheme(themeMode = themeMode) {
                MemoryCaptureNavHost(
                    onRequestRecording = ::requestRecording,
                    onStopRecording = ::stopRecordingService,
                    onExitApp = { finishAffinity() },
                )
            }
        }
    }

    private fun requestRecording() {
        if (!RecordingStateStore.transition(RecordingState.Preparing)) return

        if (Build.VERSION.SDK_INT >= 33 &&
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

        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(RecordingService.EXTRA_RESULT_DATA, resultData)
            .putExtra(RecordingService.EXTRA_STORAGE_TREE_URI, storageTreeUri)
            .putExtra(RecordingService.EXTRA_STORAGE_LABEL, storageLabel)

        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecordingService() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_STOP),
        )
    }
}
