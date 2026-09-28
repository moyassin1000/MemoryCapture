package com.memorycapture.app.recording

sealed interface RecordingState {
    data object Idle : RecordingState
    data object Preparing : RecordingState
    data object PermissionRequired : RecordingState
    data object Countdown : RecordingState
    data object Recording : RecordingState
    data object Paused : RecordingState
    data object Stopping : RecordingState
    data object Processing : RecordingState
    data object Completed : RecordingState
    data class Error(val reason: RecordingError) : RecordingState
}

enum class RecordingError {
    MediaProjectionDenied,
    MicrophoneDenied,
    AudioCaptureUnsupported,
    InsufficientStorage,
    EncoderUnavailable,
    RecordingInterrupted,
    MuxerFailure,
    UnknownError,
}
