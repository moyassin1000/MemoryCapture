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
    private var postCallRecoveryActive = false

    fun shouldAttemptRebind(
        callActive: Boolean,
        callJustEnded: Boolean,
        frameAgeMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (callActive) {
            postCallRecoveryActive = false
        } else if (callJustEnded) {
            attempts = 0
            lastAttemptElapsedMs = 0L
            postCallRecoveryActive = true
        }

        if (frameAgeMs < healthyFrameAgeMs) {
            reset()
            return false
        }

        val recoveryRelevant = callActive || postCallRecoveryActive
        if (!recoveryRelevant) return false

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

    fun hasExhaustedPostCallRecovery(
        callActive: Boolean,
        frameAgeMs: Long,
    ): Boolean =
        !callActive &&
            postCallRecoveryActive &&
            frameAgeMs >= postCallRebindThresholdMs &&
            attempts >= maxAttempts

    fun reset() {
        attempts = 0
        lastAttemptElapsedMs = 0L
        postCallRecoveryActive = false
    }

    fun attemptsForTest(): Int = attempts

    fun postCallRecoveryActiveForTest(): Boolean = postCallRecoveryActive
}
