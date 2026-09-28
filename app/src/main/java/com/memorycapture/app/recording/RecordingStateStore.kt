package com.memorycapture.app.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingStateStore {
    private val machine = RecordingStateMachine()
    private val mutableState = MutableStateFlow<RecordingState>(machine.state)
    val state: StateFlow<RecordingState> = mutableState.asStateFlow()

    @Synchronized
    fun transition(target: RecordingState): Boolean {
        val accepted = machine.transition(target)
        if (accepted) mutableState.value = machine.state
        return accepted
    }

    @Synchronized
    fun forceError(error: RecordingError) {
        machine.fail(error)
        mutableState.value = machine.state
    }

    @Synchronized
    fun reset() {
        machine.transition(RecordingState.Idle)
        mutableState.value = machine.state
    }
}
