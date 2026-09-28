package com.memorycapture.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.memorycapture.app.navigation.MemoryCaptureNavHost
import com.memorycapture.app.recording.RecordingError
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.service.RecordingService
import com.memorycapture.app.ui.theme.MemoryCaptureTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val projectionManager by lazy {
        getSystemService(MediaProjectionManager::class.java)
    }

    private val projectionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            lifecycleScope.launch {
                RecordingStateStore.transition(RecordingState.Countdown)
                repeat(3) { delay(350) }
                startRecordingService(result.resultCode, requireNotNull(result.data))
            }
        } else {
            RecordingStateStore.forceError(RecordingError.MediaProjectionDenied)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        launchProjectionPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MemoryCaptureTheme {
                MemoryCaptureNavHost(
                    onRequestRecording = ::requestRecording,
                    onStopRecording = ::stopRecordingService,
                )
            }
        }
    }

    private fun requestRecording() {
        if (!RecordingStateStore.transition(RecordingState.Preparing)) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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

    private fun startRecordingService(resultCode: Int, resultData: Intent) {
        val intent = Intent(this, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(RecordingService.EXTRA_RESULT_DATA, resultData)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecordingService() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_STOP),
        )
    }
}
