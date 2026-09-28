package com.memorycapture.app.recording

enum class AudioMode {
    None,
    Microphone,
    Device,
    DeviceAndMicrophone;

    val usesMicrophone: Boolean
        get() = this == Microphone || this == DeviceAndMicrophone

    val usesDeviceAudio: Boolean
        get() = this == Device || this == DeviceAndMicrophone
}
