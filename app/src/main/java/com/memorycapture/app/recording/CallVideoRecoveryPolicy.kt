package com.memorycapture.app.recording

class CallVideoRecoveryPolicy(
    private val callRebindThresholdMs: Long = 3_000L,
    private val postCallRebindThresholdMs: Long = 2_000L,
    private val retryIntervalMs: Long = 4_000L,
    private val healthyFrameAgeMs: Long = 2_000L,
    private val maxAttempts: Int = 3,
) {
    private var attempts = 0
    private var lastAttemptElapsedMs = 0L

    fun shouldAttemptRebind(
        callActive: Boolean,
        callJustEnded: Boolean,
        frameAgeMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (callJustEnded) {
            attempts = 0
            lastAttemptElapsedMs = 0L
        }

        if (frameAgeMs < healthyFrameAgeMs) {
            reset()
            return false
        }

        val threshold =
            if (callActive) callRebindThresholdMs else postCallRebindThresholdMs

        return frameAgeMs >= threshold &&
            attempts < maxAttempts &&
            (
                lastAttemptElapsedMs == 0L ||
                    nowElapsedMs - lastAttemptElapsedMs >= retryIntervalMs
                )
    }

    fun recordAttempt(nowElapsedMs: Long) {
        attempts += 1
        lastAttemptElapsedMs = nowElapsedMs
    }

    fun reset() {
        attempts = 0
        lastAttemptElapsedMs = 0L
    }

    fun attemptsForTest(): Int = attempts
}
