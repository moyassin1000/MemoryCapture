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
    val audioHealth: AudioCaptureHealth = AudioCaptureHealth.NotRequested,
    val voipStatus: VoipCaptureStatus = VoipCaptureStatus.Inactive,
    val storageWarning: Boolean = false,
    val workingStorageWarning: Boolean = false,
    val thermalWarning: Boolean = false,
    val microphoneMutedBySystem: Boolean = false,
    val screenInteractive: Boolean = true,
    val cpuProtectionActive: Boolean = false,
    val batteryOptimizationRestricted: Boolean = false,
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

data class CallRuntimeDiagnostics(
    val manufacturer: String = "",
    val model: String = "",
    val sdkInt: Int = 0,
    val targetSdk: Int = 0,
    val recordingActive: Boolean = false,
    val videoFrameAgeMs: Long? = null,
    val systemMicrophoneMuted: Boolean = false,
    val audio: CallAudioDiagnostics = CallAudioDiagnostics(),
    val recentEvents: List<String> = emptyList(),
)

object CallDiagnosticsStore {
    private var sessionStartedAtElapsedMs = 0L

    private val mutableStatus = MutableStateFlow(CallRuntimeDiagnostics())
    val status: StateFlow<CallRuntimeDiagnostics> = mutableStatus.asStateFlow()

    fun reset(
        manufacturer: String,
        model: String,
        sdkInt: Int,
        targetSdk: Int,
    ) {
        sessionStartedAtElapsedMs = SystemClock.elapsedRealtime()
        mutableStatus.value = CallRuntimeDiagnostics(
            manufacturer = manufacturer,
            model = model,
            sdkInt = sdkInt,
            targetSdk = targetSdk,
            recordingActive = true,
            recentEvents = listOf("+0.0s diagnostic session started"),
        )
    }

    fun update(
        recordingActive: Boolean,
        videoFrameAgeMs: Long?,
        systemMicrophoneMuted: Boolean,
        audio: CallAudioDiagnostics,
    ) {
        val previous = mutableStatus.value
        val events = previous.recentEvents.toMutableList()

        fun addEvent(message: String) {
            val elapsedMs =
                if (sessionStartedAtElapsedMs > 0L) {
                    (SystemClock.elapsedRealtime() - sessionStartedAtElapsedMs)
                        .coerceAtLeast(0L)
                } else {
                    0L
                }
            events += "+%.1fs %s".format(elapsedMs / 1000.0, message)
            while (events.size > MAX_EVENTS) {
                events.removeAt(0)
            }
        }

        if (previous.recordingActive != recordingActive) {
            addEvent("recordingActive=$recordingActive")
        }
        if (previous.audio.communicationActive != audio.communicationActive) {
            addEvent("communicationActive=${audio.communicationActive}")
        }
        if (previous.audio.microphoneSource != audio.microphoneSource) {
            addEvent("micSource=${audio.microphoneSourceLabel}")
        }
        if (
            previous.audio.microphoneSilencedBySystem !=
            audio.microphoneSilencedBySystem
        ) {
            addEvent("systemSilenced=${audio.microphoneSilencedBySystem}")
        }
        if (
            previous.audio.microphoneHasNonZeroPcm !=
            audio.microphoneHasNonZeroPcm
        ) {
            addEvent(
                "micPcm=" +
                    if (audio.microphoneHasNonZeroPcm) "signal" else "zero",
            )
        }
        if (previous.audio.speakerAssistApplied != audio.speakerAssistApplied) {
            addEvent("speakerAssist=${audio.speakerAssistApplied}")
        }
        if (previous.audio.voipStatus != audio.voipStatus) {
            addEvent("voipStatus=${audio.voipStatus}")
        }
        if (previous.audio.audioHealth != audio.audioHealth) {
            addEvent("audioHealth=${audio.audioHealth}")
        }
        if (
            previous.audio.microphoneSourceSwitchCount !=
            audio.microphoneSourceSwitchCount
        ) {
            addEvent("sourceSwitches=${audio.microphoneSourceSwitchCount}")
        }
        if (
            previous.audio.systemSilenceFallbackCount !=
            audio.systemSilenceFallbackCount
        ) {
            addEvent("systemSilenceFallbacks=${audio.systemSilenceFallbackCount}")
        }
        if (
            previous.audio.zeroPcmFallbackCount !=
            audio.zeroPcmFallbackCount
        ) {
            addEvent("zeroPcmFallbacks=${audio.zeroPcmFallbackCount}")
        }
        if (previous.systemMicrophoneMuted != systemMicrophoneMuted) {
            addEvent("systemMicMuted=$systemMicrophoneMuted")
        }

        mutableStatus.value = previous.copy(
            recordingActive = recordingActive,
            videoFrameAgeMs = videoFrameAgeMs,
            systemMicrophoneMuted = systemMicrophoneMuted,
            audio = audio,
            recentEvents = events,
        )
    }

    fun markStopped() {
        val current = mutableStatus.value
        update(
            recordingActive = false,
            videoFrameAgeMs = current.videoFrameAgeMs,
            systemMicrophoneMuted = current.systemMicrophoneMuted,
            audio = current.audio,
        )
    }

    fun report(): String {
        val current = mutableStatus.value
        return buildString {
            appendLine("MemoryCapture call diagnostics")
            appendLine("Device: ${current.manufacturer} ${current.model}")
            appendLine(
                "Android SDK: ${current.sdkInt} | targetSdk: ${current.targetSdk}",
            )
            appendLine("Recording active: ${current.recordingActive}")
            appendLine("Communication active: ${current.audio.communicationActive}")
            appendLine("Audio mode: ${current.audio.mode}")
            appendLine("Mic present: ${current.audio.microphonePresent}")
            appendLine("Mic source: ${current.audio.microphoneSourceLabel}")
            appendLine(
                "Mic silenced by Android: " +
                    current.audio.microphoneSilencedBySystem,
            )
            appendLine("System mic muted: ${current.systemMicrophoneMuted}")
            appendLine(
                "Mic PCM: bytes=${current.audio.microphoneBytesRead}, " +
                    "nonZero=${current.audio.microphoneHasNonZeroPcm}",
            )
            appendLine(
                "Last non-zero PCM age: " +
                    (current.audio.lastNonZeroPcmAgeMs?.let { "${it}ms" } ?: "n/a"),
            )
            appendLine(
                "Fallback counters: switches=${current.audio.microphoneSourceSwitchCount}, " +
                    "systemSilence=${current.audio.systemSilenceFallbackCount}, " +
                    "zeroPcm=${current.audio.zeroPcmFallbackCount}",
            )
            appendLine("Speaker Assist: ${current.audio.speakerAssistApplied}")
            appendLine("VoIP status: ${current.audio.voipStatus}")
            appendLine("Audio health: ${current.audio.audioHealth}")
            appendLine(
                "Audio heartbeat age: " +
                    (current.audio.audioHeartbeatAgeMs?.let { "${it}ms" } ?: "n/a"),
            )
            appendLine(
                "Video frame age: " +
                    (current.videoFrameAgeMs?.let { "${it}ms" } ?: "n/a"),
            )
            appendLine("Events:")
            current.recentEvents.forEach { appendLine("  $it") }
        }.trimEnd()
    }

    private const val MAX_EVENTS = 40
}

