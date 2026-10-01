package com.memorycapture.app.recording

internal object Pcm16Mixer {
    fun mix(
        micBuffer: ByteArray,
        micBytes: Int,
        playbackBuffer: ByteArray,
        playbackBytes: Int,
        out: ByteArray,
    ): Int {
        if (micBytes <= 0 && playbackBytes <= 0) return 0

        if (micBytes <= 0) {
            val bytes = playbackBytes.coerceAtLeast(0).and(-2)
            playbackBuffer.copyInto(out, endIndex = bytes)
            return bytes
        }

        if (playbackBytes <= 0) {
            val bytes = micBytes.coerceAtLeast(0).and(-2)
            micBuffer.copyInto(out, endIndex = bytes)
            return bytes
        }

        val bytes = maxOf(micBytes, playbackBytes)
            .coerceAtLeast(0)
            .and(-2)

        var index = 0
        while (index < bytes) {
            val hasMic = index + 1 < micBytes
            val hasPlayback = index + 1 < playbackBytes

            val mic = if (hasMic) {
                readSample(micBuffer, index)
            } else {
                0
            }
            val playback = if (hasPlayback) {
                readSample(playbackBuffer, index)
            } else {
                0
            }

            val mixed = when {
                hasMic && hasPlayback -> (mic + playback) / 2
                hasMic -> mic
                else -> playback
            }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            out[index] = (mixed and 0xFF).toByte()
            out[index + 1] = ((mixed shr 8) and 0xFF).toByte()
            index += 2
        }

        return bytes
    }

    private fun readSample(buffer: ByteArray, index: Int): Int =
        (
            (buffer[index].toInt() and 0xFF) or
                (buffer[index + 1].toInt() shl 8)
            ).toShort().toInt()
}
