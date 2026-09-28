package com.memorycapture.app.recording

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import com.memorycapture.app.data.preferences.AudioMode
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.max

interface AudioMuxerSink {
    fun onAudioFormat(format: MediaFormat)
    fun onAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
}

class AudioCaptureEngine(
    private val context: Context,
) {
    private var encoder: MediaCodec? = null
    private var microphoneRecord: AudioRecord? = null
    private var playbackRecord: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var failure: Throwable? = null
        private set

    private var submittedFrames = 0L

    fun start(
        projection: MediaProjection,
        mode: AudioMode,
        sink: AudioMuxerSink,
    ) {
        if (mode == AudioMode.None) return
        check(!running) { "Audio capture is already active." }

        if (
            (mode == AudioMode.DeviceAudio || mode == AudioMode.DeviceAndMic) &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        ) {
            error("Internal device audio capture requires Android 10 or newer.")
        }

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "Unable to determine audio buffer size." }
        val bufferSize = max(minBuffer * 2, PCM_BUFFER_BYTES)

        val localMic = if (
            mode == AudioMode.Microphone || mode == AudioMode.DeviceAndMic
        ) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()
                .also {
                    check(it.state == AudioRecord.STATE_INITIALIZED) {
                        "Unable to initialize microphone capture."
                    }
                }
        } else {
            null
        }

        val localPlayback = if (
            mode == AudioMode.DeviceAudio || mode == AudioMode.DeviceAndMic
        ) {
            createPlaybackRecord(
                projection = projection,
                format = format,
                bufferSize = bufferSize,
            )
        } else {
            null
        }

        val audioFormat = MediaFormat.createAudioFormat(
            AUDIO_MIME,
            SAMPLE_RATE,
            CHANNEL_COUNT,
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
        }

        val localEncoder = MediaCodec.createEncoderByType(AUDIO_MIME).apply {
            configure(
                audioFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE,
            )
            start()
        }

        microphoneRecord = localMic
        playbackRecord = localPlayback
        encoder = localEncoder
        submittedFrames = 0L
        failure = null
        running = true

        localMic?.startRecording()
        localPlayback?.startRecording()

        worker = thread(
            start = true,
            name = "MemoryCapture-AudioEncoder",
        ) {
            captureLoop(
                mode = mode,
                activeEncoder = localEncoder,
                mic = localMic,
                playback = localPlayback,
                bufferSize = bufferSize,
                sink = sink,
            )
        }
    }

    fun stopAndWait() {
        if (!running && worker == null) return
        running = false
        runCatching { microphoneRecord?.stop() }
        runCatching { playbackRecord?.stop() }

        try {
            worker?.join(STOP_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        if (worker?.isAlive == true) {
            worker?.interrupt()
        }

        clearReferences()
    }

    fun abort() {
        running = false
        runCatching { microphoneRecord?.stop() }
        runCatching { playbackRecord?.stop() }
        runCatching { worker?.interrupt() }
        try {
            worker?.join(ABORT_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        clearReferences()
    }

    private fun captureLoop(
        mode: AudioMode,
        activeEncoder: MediaCodec,
        mic: AudioRecord?,
        playback: AudioRecord?,
        bufferSize: Int,
        sink: AudioMuxerSink,
    ) {
        val micBuffer = ByteArray(bufferSize)
        val playbackBuffer = ByteArray(bufferSize)
        val mixedBuffer = ByteArray(bufferSize)

        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val bytes = when (mode) {
                    AudioMode.None -> 0

                    AudioMode.Microphone -> {
                        readAudio(requireNotNull(mic), micBuffer)
                    }

                    AudioMode.DeviceAudio -> {
                        readAudio(requireNotNull(playback), playbackBuffer)
                    }

                    AudioMode.DeviceAndMic -> {
                        val micBytes = readAudio(requireNotNull(mic), micBuffer)
                        val playbackBytes = readAudio(
                            requireNotNull(playback),
                            playbackBuffer,
                        )
                        mixPcm16(
                            micBuffer = micBuffer,
                            micBytes = micBytes,
                            playbackBuffer = playbackBuffer,
                            playbackBytes = playbackBytes,
                            out = mixedBuffer,
                        )
                    }
                }

                if (bytes > 0) {
                    val source = when (mode) {
                        AudioMode.Microphone -> micBuffer
                        AudioMode.DeviceAudio -> playbackBuffer
                        AudioMode.DeviceAndMic -> mixedBuffer
                        AudioMode.None -> mixedBuffer
                    }
                    queuePcm(activeEncoder, source, bytes, sink)
                }

                drainEncoder(
                    codec = activeEncoder,
                    sink = sink,
                    waitForEos = false,
                )
            }

            queueEndOfStream(activeEncoder, sink)
            drainEncoder(
                codec = activeEncoder,
                sink = sink,
                waitForEos = true,
            )
        } catch (error: Throwable) {
            if (running) {
                failure = error
            }
        } finally {
            running = false
            runCatching { mic?.stop() }
            runCatching { playback?.stop() }
            runCatching { mic?.release() }
            runCatching { playback?.release() }
            runCatching { activeEncoder.stop() }
            runCatching { activeEncoder.release() }
        }
    }

    private fun queuePcm(
        codec: MediaCodec,
        source: ByteArray,
        byteCount: Int,
        sink: AudioMuxerSink,
    ) {
        var offset = 0
        while (offset < byteCount && running) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex < 0) {
                drainEncoder(codec, sink, waitForEos = false)
                continue
            }

            val input = codec.getInputBuffer(inputIndex)
                ?: error("Audio encoder returned a null input buffer.")
            input.clear()

            val writable = minOf(input.remaining(), byteCount - offset)
            input.put(source, offset, writable)

            val frames = writable / BYTES_PER_FRAME
            val presentationTimeUs =
                submittedFrames * 1_000_000L / SAMPLE_RATE.toLong()

            codec.queueInputBuffer(
                inputIndex,
                0,
                writable,
                presentationTimeUs,
                0,
            )

            submittedFrames += frames
            offset += writable
        }
    }

    private fun queueEndOfStream(
        codec: MediaCodec,
        sink: AudioMuxerSink,
    ) {
        repeat(EOS_QUEUE_RETRIES) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                val ptsUs = submittedFrames * 1_000_000L / SAMPLE_RATE.toLong()
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                return
            }
            drainEncoder(codec, sink, waitForEos = false)
        }
        error("Unable to queue AAC end-of-stream.")
    }

    private fun drainEncoder(
        codec: MediaCodec,
        sink: AudioMuxerSink,
        waitForEos: Boolean,
    ) {
        val info = MediaCodec.BufferInfo()
        var idleCount = 0

        while (true) {
            val index = codec.dequeueOutputBuffer(
                info,
                if (waitForEos) CODEC_TIMEOUT_US else 0L,
            )

            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEos || idleCount++ >= EOS_DRAIN_IDLE_LIMIT) return
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    sink.onAudioFormat(codec.outputFormat)
                }

                index >= 0 -> {
                    idleCount = 0
                    val buffer = codec.getOutputBuffer(index)
                        ?: error("Audio encoder returned a null output buffer.")

                    if (
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    ) {
                        info.size = 0
                    }

                    if (info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        sink.onAudioSample(buffer, info)
                    }

                    val eos =
                        info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                    if (eos) return
                }
            }
        }
    }

    private fun readAudio(
        record: AudioRecord,
        target: ByteArray,
    ): Int {
        val read = record.read(
            target,
            0,
            target.size,
            AudioRecord.READ_BLOCKING,
        )
        return if (read > 0) read else 0
    }

    private fun mixPcm16(
        micBuffer: ByteArray,
        micBytes: Int,
        playbackBuffer: ByteArray,
        playbackBytes: Int,
        out: ByteArray,
    ): Int {
        val bytes = minOf(micBytes, playbackBytes)
            .coerceAtLeast(0)
            .and(-2)

        var index = 0
        while (index < bytes) {
            val mic = (
                (micBuffer[index].toInt() and 0xFF) or
                    (micBuffer[index + 1].toInt() shl 8)
                ).toShort().toInt()

            val playback = (
                (playbackBuffer[index].toInt() and 0xFF) or
                    (playbackBuffer[index + 1].toInt() shl 8)
                ).toShort().toInt()

            val mixed = ((mic + playback) / 2)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            out[index] = (mixed and 0xFF).toByte()
            out[index + 1] = ((mixed shr 8) and 0xFF).toByte()
            index += 2
        }

        return bytes
    }

    private fun createPlaybackRecord(
        projection: MediaProjection,
        format: AudioFormat,
        bufferSize: Int,
    ): AudioRecord {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setBufferSizeInBytes(bufferSize)
            .build()
            .also {
                check(it.state == AudioRecord.STATE_INITIALIZED) {
                    "Unable to initialize device audio capture."
                }
            }
    }

    private fun clearReferences() {
        encoder = null
        microphoneRecord = null
        playbackRecord = null
        worker = null
    }

    companion object {
        private const val AUDIO_MIME = "audio/mp4a-latm"
        private const val SAMPLE_RATE = 48_000
        private const val CHANNEL_COUNT = 1
        private const val AUDIO_BIT_RATE = 128_000
        private const val PCM_BUFFER_BYTES = 16_384
        private const val BYTES_PER_FRAME = 2
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val STOP_JOIN_TIMEOUT_MS = 5_000L
        private const val ABORT_JOIN_TIMEOUT_MS = 1_000L
        private const val EOS_QUEUE_RETRIES = 100
        private const val EOS_DRAIN_IDLE_LIMIT = 100
    }
}
