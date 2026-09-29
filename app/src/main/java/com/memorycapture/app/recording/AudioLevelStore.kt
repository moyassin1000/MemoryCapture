package com.memorycapture.app.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class AudioLevelState(
    val microphone: Float = 0f,
    val deviceAudio: Float = 0f,
)

object AudioLevelStore {
    private val mutableState = MutableStateFlow(AudioLevelState())
    val state: StateFlow<AudioLevelState> = mutableState.asStateFlow()

    fun updateMicrophone(buffer: ByteArray, byteCount: Int) {
        mutableState.value = mutableState.value.copy(
            microphone = pcm16Level(buffer, byteCount),
        )
    }

    fun updateDeviceAudio(buffer: ByteArray, byteCount: Int) {
        mutableState.value = mutableState.value.copy(
            deviceAudio = pcm16Level(buffer, byteCount),
        )
    }

    fun clearMicrophone() {
        mutableState.value = mutableState.value.copy(microphone = 0f)
    }

    fun clearDeviceAudio() {
        mutableState.value = mutableState.value.copy(deviceAudio = 0f)
    }

    fun reset() {
        mutableState.value = AudioLevelState()
    }

    private fun pcm16Level(buffer: ByteArray, byteCount: Int): Float {
        val usable = byteCount.coerceAtMost(buffer.size).coerceAtLeast(0) and -2
        if (usable < 2) return 0f

        var sum = 0.0
        var samples = 0
        var index = 0

        while (index < usable) {
            val sample = (
                (buffer[index].toInt() and 0xFF) or
                    (buffer[index + 1].toInt() shl 8)
                ).toShort().toInt()

            val normalized = sample / 32768.0
            sum += normalized * normalized
            samples += 1
            index += 2
        }

        if (samples == 0) return 0f

        val rms = sqrt(sum / samples)
        return (rms * 3.2).toFloat().coerceIn(0f, 1f)
    }
}
