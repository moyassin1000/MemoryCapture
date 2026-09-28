package com.memorycapture.app.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecorderHealth {
    Idle,
    Starting,
    Healthy,
    Warning,
    Error,
}

data class RecorderDiagnostics(
    val health: RecorderHealth = RecorderHealth.Idle,
    val codecName: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Int = 0,
    val bitRate: Int = 0,
    val encodedFrames: Long = 0L,
    val lastPresentationTimeUs: Long = 0L,
    val captureVisible: Boolean = true,
    val capturedContentWidth: Int = 0,
    val capturedContentHeight: Int = 0,
    val message: String? = null,
)

object RecorderDiagnosticsStore {
    private val mutableState = MutableStateFlow(RecorderDiagnostics())
    val state: StateFlow<RecorderDiagnostics> = mutableState.asStateFlow()

    fun starting(
        codecName: String,
        width: Int,
        height: Int,
        frameRate: Int,
        bitRate: Int,
    ) {
        mutableState.value = RecorderDiagnostics(
            health = RecorderHealth.Starting,
            codecName = codecName,
            width = width,
            height = height,
            frameRate = frameRate,
            bitRate = bitRate,
            captureVisible = true,
        )
    }

    fun onEncodedFrame(count: Long, presentationTimeUs: Long) {
        val current = mutableState.value
        mutableState.value = current.copy(
            health = if (current.captureVisible) RecorderHealth.Healthy else RecorderHealth.Warning,
            encodedFrames = count,
            lastPresentationTimeUs = presentationTimeUs,
            message = null,
        )
    }

    fun setCaptureVisible(visible: Boolean) {
        val current = mutableState.value
        mutableState.value = current.copy(
            captureVisible = visible,
            health = when {
                !visible -> RecorderHealth.Warning
                current.encodedFrames > 0L -> RecorderHealth.Healthy
                else -> current.health
            },
        )
    }

    fun setCapturedContentSize(width: Int, height: Int) {
        mutableState.value = mutableState.value.copy(
            capturedContentWidth = width,
            capturedContentHeight = height,
        )
    }

    fun warning(message: String) {
        mutableState.value = mutableState.value.copy(
            health = RecorderHealth.Warning,
            message = message,
        )
    }

    fun error(message: String) {
        mutableState.value = mutableState.value.copy(
            health = RecorderHealth.Error,
            message = message,
        )
    }

    fun stopped() {
        val current = mutableState.value
        mutableState.value = current.copy(
            health = if (current.encodedFrames > 0L) RecorderHealth.Healthy else current.health,
        )
    }

    fun reset() {
        mutableState.value = RecorderDiagnostics()
    }
}
