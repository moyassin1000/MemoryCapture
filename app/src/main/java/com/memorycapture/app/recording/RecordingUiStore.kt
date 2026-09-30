package com.memorycapture.app.recording

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CountdownStore {
    private val mutableSeconds = MutableStateFlow<Int?>(null)
    val seconds: StateFlow<Int?> = mutableSeconds.asStateFlow()

    fun show(seconds: Int) {
        mutableSeconds.value = seconds
    }

    fun clear() {
        mutableSeconds.value = null
    }
}

data class SavedRecordingInfo(
    val displayName: String,
    val location: String,
)

object SavedRecordingStore {
    private val mutableRecording = MutableStateFlow<SavedRecordingInfo?>(null)
    val recording: StateFlow<SavedRecordingInfo?> = mutableRecording.asStateFlow()

    fun setSaved(displayName: String, location: String) {
        mutableRecording.value = SavedRecordingInfo(displayName, location)
    }

    fun clear() {
        mutableRecording.value = null
    }
}

object RecordingSessionStore {
    private val mutableStartedAt = MutableStateFlow<Long?>(null)
    val startedAtElapsedRealtime: StateFlow<Long?> = mutableStartedAt.asStateFlow()

    private val mutablePausedAt = MutableStateFlow<Long?>(null)
    val pausedAtElapsedRealtime: StateFlow<Long?> = mutablePausedAt.asStateFlow()

    private val mutableAccumulatedPausedMs = MutableStateFlow(0L)
    val accumulatedPausedMs: StateFlow<Long> = mutableAccumulatedPausedMs.asStateFlow()

    fun markStarted() {
        mutableStartedAt.value = SystemClock.elapsedRealtime()
        mutablePausedAt.value = null
        mutableAccumulatedPausedMs.value = 0L
    }

    fun markPaused() {
        if (mutableStartedAt.value == null || mutablePausedAt.value != null) return
        mutablePausedAt.value = SystemClock.elapsedRealtime()
    }

    fun markResumed() {
        val pausedAt = mutablePausedAt.value ?: return
        mutableAccumulatedPausedMs.value +=
            (SystemClock.elapsedRealtime() - pausedAt).coerceAtLeast(0L)
        mutablePausedAt.value = null
    }

    fun clear() {
        mutableStartedAt.value = null
        mutablePausedAt.value = null
        mutableAccumulatedPausedMs.value = 0L
    }
}
