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

    private val mutableHighlights = MutableStateFlow<List<Long>>(emptyList())
    val highlightsMillis: StateFlow<List<Long>> = mutableHighlights.asStateFlow()

    fun markStarted() {
        mutableStartedAt.value = SystemClock.elapsedRealtime()
        mutablePausedAt.value = null
        mutableAccumulatedPausedMs.value = 0L
        mutableHighlights.value = emptyList()
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

    fun markHighlight(): Long? {
        val startedAt = mutableStartedAt.value ?: return null
        val now = SystemClock.elapsedRealtime()
        val pausedAt = mutablePausedAt.value
        val currentPauseMs = pausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        val activeMs =
            (now - startedAt - mutableAccumulatedPausedMs.value - currentPauseMs)
                .coerceAtLeast(0L)

        val existing = mutableHighlights.value
        if (existing.lastOrNull()?.let { activeMs - it < MIN_HIGHLIGHT_GAP_MS } == true) {
            return existing.last()
        }

        mutableHighlights.value = existing + activeMs
        return activeMs
    }

    fun snapshotHighlights(): List<Long> = mutableHighlights.value

    fun clear() {
        mutableStartedAt.value = null
        mutablePausedAt.value = null
        mutableAccumulatedPausedMs.value = 0L
        mutableHighlights.value = emptyList()
    }

    private const val MIN_HIGHLIGHT_GAP_MS = 750L
}


enum class GuardianThermalLevel {
    Normal,
    Warm,
    Hot,
    Critical,
}

data class RecordingGuardianStatus(
    val destinationAvailableBytes: Long? = null,
    val workingAvailableBytes: Long? = null,
    val estimatedRemainingSeconds: Long? = null,
    val destinationSpaceKnown: Boolean = true,
    val thermalLevel: GuardianThermalLevel = GuardianThermalLevel.Normal,
    val storageWarning: Boolean = false,
    val workingStorageWarning: Boolean = false,
    val thermalWarning: Boolean = false,
)

object RecordingGuardianStore {
    private val mutableStatus = MutableStateFlow(RecordingGuardianStatus())
    val status: StateFlow<RecordingGuardianStatus> = mutableStatus.asStateFlow()

    fun update(status: RecordingGuardianStatus) {
        mutableStatus.value = status
    }

    fun clear() {
        mutableStatus.value = RecordingGuardianStatus()
    }
}
