package com.memorycapture.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16MixerTest {
    @Test
    fun unequalChunks_preserveLongerMicTimeline() {
        val mic = samples(1000, 2000)
        val playback = samples(3000)
        val out = ByteArray(4)

        val bytes = Pcm16Mixer.mix(
            micBuffer = mic,
            micBytes = mic.size,
            playbackBuffer = playback,
            playbackBytes = playback.size,
            out = out,
        )

        assertEquals(4, bytes)
        assertEquals(2000, readSample(out, 0))
        assertEquals(2000, readSample(out, 2))
    }

    @Test
    fun unequalChunks_preserveLongerPlaybackTimeline() {
        val mic = samples(1000)
        val playback = samples(3000, -2000)
        val out = ByteArray(4)

        val bytes = Pcm16Mixer.mix(
            micBuffer = mic,
            micBytes = mic.size,
            playbackBuffer = playback,
            playbackBytes = playback.size,
            out = out,
        )

        assertEquals(4, bytes)
        assertEquals(2000, readSample(out, 0))
        assertEquals(-2000, readSample(out, 2))
    }

    @Test
    fun singleSource_keepsFullLevel() {
        val mic = samples(12000, -12000)
        val out = ByteArray(4)

        val bytes = Pcm16Mixer.mix(
            micBuffer = mic,
            micBytes = mic.size,
            playbackBuffer = ByteArray(0),
            playbackBytes = 0,
            out = out,
        )

        assertEquals(4, bytes)
        assertEquals(12000, readSample(out, 0))
        assertEquals(-12000, readSample(out, 2))
    }

    private fun samples(vararg values: Int): ByteArray {
        val result = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            val offset = index * 2
            result[offset] = (value and 0xFF).toByte()
            result[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun readSample(buffer: ByteArray, index: Int): Int =
        (
            (buffer[index].toInt() and 0xFF) or
                (buffer[index + 1].toInt() shl 8)
            ).toShort().toInt()
}
