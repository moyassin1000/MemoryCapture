package com.memorycapture.app.recording

class RecordingStateMachine(
    initial: RecordingState = RecordingState.Idle,
) {
    var state: RecordingState = initial
        private set

    @Synchronized
    fun transition(target: RecordingState): Boolean {
        if (!isAllowed(state, target)) return false
        state = target
        return true
    }

    @Synchronized
    fun fail(error: RecordingError) {
        state = RecordingState.Error(error)
    }

    private fun isAllowed(from: RecordingState, to: RecordingState): Boolean = when (from) {
        RecordingState.Idle -> to is RecordingState.Preparing || to is RecordingState.PermissionRequired
        RecordingState.Preparing -> to is RecordingState.PermissionRequired || to is RecordingState.Countdown || to is RecordingState.Error || to is RecordingState.Idle
        RecordingState.PermissionRequired -> to is RecordingState.Countdown || to is RecordingState.Error || to is RecordingState.Idle
        RecordingState.Countdown -> to is RecordingState.Recording || to is RecordingState.Error || to is RecordingState.Idle
        RecordingState.Recording -> to is RecordingState.Paused || to is RecordingState.Stopping || to is RecordingState.Error
        RecordingState.Paused -> to is RecordingState.Recording || to is RecordingState.Stopping || to is RecordingState.Error
        RecordingState.Stopping -> to is RecordingState.Processing || to is RecordingState.Completed || to is RecordingState.Error
        RecordingState.Processing -> to is RecordingState.Completed || to is RecordingState.Error
        RecordingState.Completed -> to is RecordingState.Idle || to is RecordingState.Preparing
        is RecordingState.Error -> to is RecordingState.Idle || to is RecordingState.Preparing
    }
}
