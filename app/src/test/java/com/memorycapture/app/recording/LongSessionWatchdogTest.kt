package com.memorycapture.app.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongSessionWatchdogTest {
    @Test
    fun singleTransientFatalCheck_doesNotStopRecording() {
        val watchdog = LongSessionWatchdog(
            fatalChecksRequired = 2,
            videoStallChecksRequired = 3,
            audioStallChecksRequired = 3,
        )

        val decision = watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = true,
                videoDrainStalled = false,
                audioCaptureStalled = false,
            ),
        )

        assertFalse(decision.stopRecording)
    }

    @Test
    fun repeatedFatalChecks_stopRecording() {
        val watchdog = LongSessionWatchdog(
            fatalChecksRequired = 2,
            videoStallChecksRequired = 3,
            audioStallChecksRequired = 3,
        )

        watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = true,
                videoDrainStalled = false,
                audioCaptureStalled = false,
            ),
        )
        val decision = watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = true,
                videoDrainStalled = false,
                audioCaptureStalled = false,
            ),
        )

        assertTrue(decision.stopRecording)
    }

    @Test
    fun repeatedAudioStall_degradesAudioWithoutStoppingVideo() {
        val watchdog = LongSessionWatchdog(
            fatalChecksRequired = 2,
            videoStallChecksRequired = 3,
            audioStallChecksRequired = 3,
        )

        repeat(2) {
            val decision = watchdog.evaluate(
                LongSessionWatchdogInput(
                    fatalRuntimeFailure = false,
                    videoDrainStalled = false,
                    audioCaptureStalled = true,
                ),
            )
            assertFalse(decision.degradeAudio)
            assertFalse(decision.stopRecording)
        }

        val decision = watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = false,
                videoDrainStalled = false,
                audioCaptureStalled = true,
            ),
        )

        assertTrue(decision.degradeAudio)
        assertFalse(decision.stopRecording)
    }

    @Test
    fun healthyCheck_resetsStallCounters() {
        val watchdog = LongSessionWatchdog(
            fatalChecksRequired = 2,
            videoStallChecksRequired = 2,
            audioStallChecksRequired = 2,
        )

        watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = false,
                videoDrainStalled = true,
                audioCaptureStalled = false,
            ),
        )
        watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = false,
                videoDrainStalled = false,
                audioCaptureStalled = false,
            ),
        )
        val decision = watchdog.evaluate(
            LongSessionWatchdogInput(
                fatalRuntimeFailure = false,
                videoDrainStalled = true,
                audioCaptureStalled = false,
            ),
        )

        assertFalse(decision.stopRecording)
    }
}
