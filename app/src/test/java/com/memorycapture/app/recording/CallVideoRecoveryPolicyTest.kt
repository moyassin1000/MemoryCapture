package com.memorycapture.app.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CallVideoRecoveryPolicyTest {
    @Test
    fun healthyFrames_doNotTriggerRebind() {
        val policy = CallVideoRecoveryPolicy()

        assertFalse(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 1_000L,
                nowElapsedMs = 5_000L,
            ),
        )
    }

    @Test
    fun activeCallStall_triggersBoundedRebinds() {
        val policy = CallVideoRecoveryPolicy(
            retryIntervalMs = 1_000L,
            maxAttempts = 2,
        )

        assertTrue(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 4_000L,
                nowElapsedMs = 4_000L,
            ),
        )
        policy.recordAttempt(4_000L)

        assertFalse(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 5_000L,
                nowElapsedMs = 4_500L,
            ),
        )

        assertTrue(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 6_000L,
                nowElapsedMs = 5_100L,
            ),
        )
        policy.recordAttempt(5_100L)

        assertFalse(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 8_000L,
                nowElapsedMs = 7_000L,
            ),
        )
        assertEquals(2, policy.attemptsForTest())
    }

    @Test
    fun callEnd_resetsAttemptBudget() {
        val policy = CallVideoRecoveryPolicy(
            retryIntervalMs = 1_000L,
            maxAttempts = 1,
        )

        assertTrue(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 4_000L,
                nowElapsedMs = 4_000L,
            ),
        )
        policy.recordAttempt(4_000L)

        assertTrue(
            policy.shouldAttemptRebind(
                callActive = false,
                callJustEnded = true,
                frameAgeMs = 3_000L,
                nowElapsedMs = 5_000L,
            ),
        )
    }

    @Test
    fun realFrameRecovery_resetsAttempts() {
        val policy = CallVideoRecoveryPolicy(maxAttempts = 1)

        assertTrue(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 4_000L,
                nowElapsedMs = 4_000L,
            ),
        )
        policy.recordAttempt(4_000L)

        assertFalse(
            policy.shouldAttemptRebind(
                callActive = true,
                callJustEnded = false,
                frameAgeMs = 500L,
                nowElapsedMs = 5_000L,
            ),
        )
        assertEquals(0, policy.attemptsForTest())
    }
}
