package com.memorycapture.app.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateMachineTest {
    @Test
    fun startToStopFlow_isAccepted() {
        val machine = RecordingStateMachine()
        assertTrue(machine.transition(RecordingState.Preparing))
        assertTrue(machine.transition(RecordingState.PermissionRequired))
        assertTrue(machine.transition(RecordingState.Countdown))
        assertTrue(machine.transition(RecordingState.Recording))
        assertTrue(machine.transition(RecordingState.Stopping))
        assertTrue(machine.transition(RecordingState.Completed))
    }

    @Test
    fun rapidInvalidTransition_isRejected() {
        val machine = RecordingStateMachine()
        assertFalse(machine.transition(RecordingState.Paused))
        assertTrue(machine.state is RecordingState.Idle)
    }

    @Test
    fun pauseResumeFlow_isAccepted() {
        val machine = RecordingStateMachine(RecordingState.Recording)
        assertTrue(machine.transition(RecordingState.Paused))
        assertTrue(machine.transition(RecordingState.Recording))
        assertTrue(machine.transition(RecordingState.Stopping))
    }
    @Test
    fun errorCanRecoverToIdle() {
        val machine = RecordingStateMachine(RecordingState.Recording)
        machine.fail(RecordingError.RecordingInterrupted)
        assertTrue(machine.state is RecordingState.Error)
        assertTrue(machine.transition(RecordingState.Idle))
    }

}
