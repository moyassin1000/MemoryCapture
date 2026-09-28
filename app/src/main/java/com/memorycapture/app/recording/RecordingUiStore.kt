package com.memorycapture.app.recording

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
