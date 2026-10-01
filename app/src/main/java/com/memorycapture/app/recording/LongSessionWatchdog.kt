package com.memorycapture.app.recording

data class LongSessionWatchdogInput(
    val fatalRuntimeFailure: Boolean,
    val videoDrainStalled: Boolean,
    val audioCaptureStalled: Boolean,
)

data class LongSessionWatchdogDecision(
    val stopRecording: Boolean,
    val degradeAudio: Boolean,
)

class LongSessionWatchdog(
    private val fatalChecksRequired: Int = DEFAULT_FATAL_CHECKS,
    private val videoStallChecksRequired: Int = DEFAULT_VIDEO_STALL_CHECKS,
    private val audioStallChecksRequired: Int = DEFAULT_AUDIO_STALL_CHECKS,
) {
    private var fatalChecks = 0
    private var videoStallChecks = 0
    private var audioStallChecks = 0

    fun evaluate(input: LongSessionWatchdogInput): LongSessionWatchdogDecision {
        fatalChecks = if (input.fatalRuntimeFailure) fatalChecks + 1 else 0
        videoStallChecks = if (input.videoDrainStalled) videoStallChecks + 1 else 0
        audioStallChecks = if (input.audioCaptureStalled) audioStallChecks + 1 else 0

        val degradeAudio = audioStallChecks >= audioStallChecksRequired
        if (degradeAudio) {
            audioStallChecks = 0
        }

        return LongSessionWatchdogDecision(
            stopRecording =
                fatalChecks >= fatalChecksRequired ||
                    videoStallChecks >= videoStallChecksRequired,
            degradeAudio = degradeAudio,
        )
    }

    fun reset() {
        fatalChecks = 0
        videoStallChecks = 0
        audioStallChecks = 0
    }

    companion object {
        private const val DEFAULT_FATAL_CHECKS = 2
        private const val DEFAULT_VIDEO_STALL_CHECKS = 3
        private const val DEFAULT_AUDIO_STALL_CHECKS = 3
    }
}
