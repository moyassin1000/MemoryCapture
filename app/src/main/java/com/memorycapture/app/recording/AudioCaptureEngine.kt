package com.memorycapture.app.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.memorycapture.app.data.preferences.AudioMode
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import kotlin.math.max

interface AudioMuxerSink {
    fun onAudioFormat(format: MediaFormat)
    fun onAudioSample(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
}

enum class AudioCaptureHealth {
    NotRequested,
    Healthy,
    MicrophoneLost,
    DeviceAudioLost,
    AllAudioLost,
}

enum class VoipCaptureStatus {
    Inactive,
    CallDetected,
    SpeakerAssistActive,
    SilencedBySystem,
    NoMicrophonePath,
}

@SuppressLint("MissingPermission")
class AudioCaptureEngine(
    private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var encoder: MediaCodec? = null
    @Volatile
    private var microphoneRecord: AudioRecord? = null
    private var playbackRecord: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    var failure: Throwable? = null
        private set

    @Volatile
    private var runtimeHealth = AudioCaptureHealth.NotRequested

    @Volatile
    private var voipStatus = VoipCaptureStatus.Inactive

    @Volatile
    private var voipAssistEnabled = false

    @Volatile
    private var callMicSilencedBySystem = false

    @Volatile
    private var activeMicSource = MediaRecorder.AudioSource.MIC

    @Volatile
    private var accessibilityAssistEnabled = false

    private var speakerAssistApplied = false
    private var previousCommunicationDeviceId: Int? = null
    private var legacySpeakerWasOn = false

    @Volatile
    private var lastWorkerHeartbeatElapsedMs = 0L

    private var submittedFrames = 0L

    fun start(
        projection: MediaProjection,
        mode: AudioMode,
        preferredMicDeviceId: Int = -1,
        voipCaptureAssistEnabled: Boolean = false,
        sink: AudioMuxerSink,
    ) {
        if (mode == AudioMode.None) return
        check(!running) { "Audio capture is already active." }
        check(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        ) {
            "RECORD_AUDIO permission is required for audio recording."
        }

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
            createMicrophoneRecord(
                format = format,
                bufferSize = bufferSize,
                preferredMicDeviceId = preferredMicDeviceId,
                audioSource = MediaRecorder.AudioSource.MIC,
            )
        } else {
            null
        }

        val localPlayback = if (
            mode == AudioMode.DeviceAudio || mode == AudioMode.DeviceAndMic
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createPlaybackRecord(
                    projection = projection,
                    format = format,
                    bufferSize = bufferSize,
                )
            } else {
                error("Internal device audio capture requires Android 10 or newer.")
            }
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
        runtimeHealth = AudioCaptureHealth.Healthy
        voipStatus = VoipCaptureStatus.Inactive
        voipAssistEnabled = voipCaptureAssistEnabled
        callMicSilencedBySystem = false
        activeMicSource = MediaRecorder.AudioSource.MIC
        accessibilityAssistEnabled =
            CallCaptureCompatibility.isAccessibilityAssistEnabled(context)
        previousCommunicationDeviceId = null
        lastWorkerHeartbeatElapsedMs = SystemClock.elapsedRealtime()
        paused = false
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
                initialMic = localMic,
                playback = localPlayback,
                audioFormat = format,
                preferredMicDeviceId = preferredMicDeviceId,
                bufferSize = bufferSize,
                sink = sink,
            )
        }
    }

    fun pause() {
        if (!running || paused) return
        paused = true
        runCatching { microphoneRecord?.stop() }
        runCatching { playbackRecord?.stop() }
    }

    fun resume() {
        if (!running || !paused) return

        try {
            microphoneRecord?.startRecording()
            playbackRecord?.startRecording()
            paused = false
        } catch (error: Throwable) {
            failure = error
            runCatching { microphoneRecord?.stop() }
            runCatching { playbackRecord?.stop() }
            throw error
        }
    }

    fun stopAndWait() {
        if (!running && worker == null) return
        running = false
        paused = false
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
        paused = false
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
        initialMic: AudioRecord?,
        playback: AudioRecord?,
        audioFormat: AudioFormat,
        preferredMicDeviceId: Int,
        bufferSize: Int,
        sink: AudioMuxerSink,
    ) {
        val micBuffer = ByteArray(bufferSize)
        val playbackBuffer = ByteArray(bufferSize)
        val mixedBuffer = ByteArray(bufferSize)

        var mic = initialMic
        var micAvailable = mic != null
        var playbackAvailable = playback != null
        var micSourceIndex = 0
        var lastMicProbeElapsedMs = 0L
        var lastMicRecoveryAttemptElapsedMs = 0L
        var lastAccessibilityCheckElapsedMs = 0L
        var lastCommunicationActive = false
        var lastSpeakerAssistAttemptElapsedMs = 0L

        try {
            while (running && !Thread.currentThread().isInterrupted) {
                lastWorkerHeartbeatElapsedMs = SystemClock.elapsedRealtime()

                if (paused) {
                    Thread.sleep(PAUSE_POLL_MS)
                    continue
                }

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val communicationActive = isCommunicationActive()
                val microphoneRequested =
                    mode == AudioMode.Microphone || mode == AudioMode.DeviceAndMic

                if (
                    voipAssistEnabled &&
                    nowElapsedMs - lastAccessibilityCheckElapsedMs >=
                        ACCESSIBILITY_ASSIST_CHECK_INTERVAL_MS
                ) {
                    lastAccessibilityCheckElapsedMs = nowElapsedMs
                    accessibilityAssistEnabled =
                        CallCaptureCompatibility.isAccessibilityAssistEnabled(context)
                }

                if (
                    communicationActive &&
                    !lastCommunicationActive &&
                    microphoneRequested &&
                    accessibilityAssistEnabled &&
                    mic != null &&
                    micSourceIndex == 0
                ) {
                    val replacement = replaceMicrophoneRecord(
                        current = mic,
                        format = audioFormat,
                        bufferSize = bufferSize,
                        preferredMicDeviceId = preferredMicDeviceId,
                        audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    )
                    if (replacement !== mic) {
                        mic = replacement
                        micAvailable = true
                        micSourceIndex = 1
                        callMicSilencedBySystem = false
                    }
                }

                if (communicationActive) {
                    if (
                        voipAssistEnabled &&
                        nowElapsedMs - lastSpeakerAssistAttemptElapsedMs >=
                            SPEAKER_ASSIST_REASSERT_INTERVAL_MS
                    ) {
                        lastSpeakerAssistAttemptElapsedMs = nowElapsedMs
                        applySpeakerAssist()
                    }
                    voipStatus = when {
                        callMicSilencedBySystem -> VoipCaptureStatus.SilencedBySystem
                        microphoneRecord == null -> VoipCaptureStatus.NoMicrophonePath
                        speakerAssistApplied -> VoipCaptureStatus.SpeakerAssistActive
                        else -> VoipCaptureStatus.CallDetected
                    }
                } else if (lastCommunicationActive) {
                    restoreSpeakerAssist()
                    callMicSilencedBySystem = false
                    voipStatus = VoipCaptureStatus.Inactive
                }
                lastCommunicationActive = communicationActive

                if (
                    microphoneRequested &&
                    voipAssistEnabled &&
                    micAvailable &&
                    mic != null &&
                    nowElapsedMs - lastMicProbeElapsedMs >=
                        CALL_MIC_PROBE_INTERVAL_MS
                ) {
                    lastMicProbeElapsedMs = nowElapsedMs
                    val silenced = isMicrophoneSilencedBySystem(mic)
                    callMicSilencedBySystem = communicationActive && silenced

                    if (
                        communicationActive &&
                        silenced &&
                        accessibilityAssistEnabled &&
                        micSourceIndex < CALL_MIC_AUDIO_SOURCES.lastIndex
                    ) {
                        val nextIndex = micSourceIndex + 1
                        val replacement = replaceMicrophoneRecord(
                            current = mic,
                            format = audioFormat,
                            bufferSize = bufferSize,
                            preferredMicDeviceId = preferredMicDeviceId,
                            audioSource = CALL_MIC_AUDIO_SOURCES[nextIndex],
                        )
                        if (replacement != null && replacement !== mic) {
                            mic = replacement
                            micAvailable = true
                            micSourceIndex = nextIndex
                            lastMicRecoveryAttemptElapsedMs = nowElapsedMs
                            callMicSilencedBySystem = false
                        }
                    }
                }

                if (
                    microphoneRequested &&
                    voipAssistEnabled &&
                    !micAvailable &&
                    nowElapsedMs - lastMicRecoveryAttemptElapsedMs >=
                        CALL_MIC_RECOVERY_INTERVAL_MS
                ) {
                    lastMicRecoveryAttemptElapsedMs = nowElapsedMs
                    val targetIndex = if (
                        communicationActive &&
                        accessibilityAssistEnabled
                    ) {
                        (micSourceIndex + 1)
                            .coerceAtMost(CALL_MIC_AUDIO_SOURCES.lastIndex)
                    } else {
                        0
                    }
                    val replacement = replaceMicrophoneRecord(
                        current = mic,
                        format = audioFormat,
                        bufferSize = bufferSize,
                        preferredMicDeviceId = preferredMicDeviceId,
                        audioSource = CALL_MIC_AUDIO_SOURCES[targetIndex],
                    )
                    if (replacement != null && replacement !== mic) {
                        mic = replacement
                        micAvailable = true
                        micSourceIndex = targetIndex
                        callMicSilencedBySystem = false
                    }
                }

                if (
                    microphoneRequested &&
                    voipAssistEnabled &&
                    !communicationActive &&
                    (mic == null || micSourceIndex != 0) &&
                    nowElapsedMs - lastMicRecoveryAttemptElapsedMs >=
                        CALL_MIC_RECOVERY_INTERVAL_MS
                ) {
                    lastMicRecoveryAttemptElapsedMs = nowElapsedMs
                    val replacement = replaceMicrophoneRecord(
                        current = mic,
                        format = audioFormat,
                        bufferSize = bufferSize,
                        preferredMicDeviceId = preferredMicDeviceId,
                        audioSource = MediaRecorder.AudioSource.MIC,
                    )
                    if (replacement != null && replacement !== mic) {
                        mic = replacement
                        micAvailable = true
                        micSourceIndex = 0
                        callMicSilencedBySystem = false
                    }
                }

                val micBytes = if (micAvailable && mic != null) {
                    val read = readAudio(mic, micBuffer)
                    if (read < 0) {
                        micAvailable = false
                        if (!voipAssistEnabled) {
                            updateRuntimeHealth(
                                mode = mode,
                                micAvailable = false,
                                playbackAvailable = playbackAvailable,
                            )
                        }
                        0
                    } else {
                        read
                    }
                } else if (
                    microphoneRequested &&
                    voipAssistEnabled
                ) {
                    val silenceBytes =
                        minOf(SYNTHETIC_SILENCE_BYTES, micBuffer.size).and(-2)
                    micBuffer.fill(0, 0, silenceBytes)
                    silenceBytes
                } else {
                    0
                }

                val playbackBytes = if (
                    communicationActive &&
                    mode == AudioMode.DeviceAndMic
                ) {
                    0
                } else if (playbackAvailable && playback != null) {
                    val read = readAudio(playback, playbackBuffer)
                    if (read < 0) {
                        playbackAvailable = false
                        updateRuntimeHealth(
                            mode = mode,
                            micAvailable = micAvailable,
                            playbackAvailable = false,
                        )
                        0
                    } else {
                        read
                    }
                } else {
                    0
                }

                lastWorkerHeartbeatElapsedMs = SystemClock.elapsedRealtime()

                val effectiveMicBytes =
                    if (
                        communicationActive &&
                        microphoneRequested &&
                        voipAssistEnabled &&
                        micBytes <= 0
                    ) {
                        val silenceBytes =
                            minOf(SYNTHETIC_SILENCE_BYTES, micBuffer.size).and(-2)
                        micBuffer.fill(0, 0, silenceBytes)
                        silenceBytes
                    } else {
                        micBytes
                    }

                val bytes = when (mode) {
                    AudioMode.None -> 0
                    AudioMode.Microphone -> effectiveMicBytes
                    AudioMode.DeviceAudio -> playbackBytes
                    AudioMode.DeviceAndMic -> {
                        if (communicationActive) {
                            copyPcm16(
                                source = micBuffer,
                                byteCount = effectiveMicBytes,
                                out = mixedBuffer,
                            )
                        } else {
                            mixPcm16(
                                micBuffer = micBuffer,
                                micBytes = effectiveMicBytes,
                                playbackBuffer = playbackBuffer,
                                playbackBytes = playbackBytes,
                                out = mixedBuffer,
                            )
                        }
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

                if (
                    communicationActive &&
                    microphoneRequested &&
                    voipAssistEnabled &&
                    micBytes <= 0 &&
                    bytes > 0
                ) {
                    Thread.sleep(SYNTHETIC_SILENCE_PACE_MS)
                } else if (bytes == 0 && (micAvailable || playbackAvailable)) {
                    Thread.sleep(AUDIO_IDLE_BACKOFF_MS)
                }

                val noRequestedSourceAvailable = when (mode) {
                    AudioMode.None -> true
                    AudioMode.Microphone -> !micAvailable
                    AudioMode.DeviceAudio -> !playbackAvailable
                    AudioMode.DeviceAndMic -> !micAvailable && !playbackAvailable
                }
                if (
                    noRequestedSourceAvailable &&
                    !(microphoneRequested && voipAssistEnabled)
                ) {
                    break
                }
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
    ): Int =
        record.read(
            target,
            0,
            minOf(target.size, PCM_READ_CHUNK_BYTES),
            AudioRecord.READ_NON_BLOCKING,
        )

    private fun updateRuntimeHealth(
        mode: AudioMode,
        micAvailable: Boolean,
        playbackAvailable: Boolean,
    ) {
        runtimeHealth = when (mode) {
            AudioMode.None -> AudioCaptureHealth.NotRequested
            AudioMode.Microphone -> {
                if (micAvailable) {
                    AudioCaptureHealth.Healthy
                } else {
                    AudioCaptureHealth.MicrophoneLost
                }
            }
            AudioMode.DeviceAudio -> {
                if (playbackAvailable) {
                    AudioCaptureHealth.Healthy
                } else {
                    AudioCaptureHealth.DeviceAudioLost
                }
            }
            AudioMode.DeviceAndMic -> when {
                !micAvailable && !playbackAvailable ->
                    AudioCaptureHealth.AllAudioLost
                !micAvailable -> AudioCaptureHealth.MicrophoneLost
                !playbackAvailable -> AudioCaptureHealth.DeviceAudioLost
                else -> AudioCaptureHealth.Healthy
            }
        }
    }

    fun health(): AudioCaptureHealth = runtimeHealth

    fun refreshVoipCaptureStatus(): VoipCaptureStatus {
        if (!running || paused) {
            restoreSpeakerAssist()
            voipStatus = VoipCaptureStatus.Inactive
            return voipStatus
        }

        val communicationActive = isCommunicationActive()

        if (!communicationActive) {
            restoreSpeakerAssist()
            voipStatus = VoipCaptureStatus.Inactive
            return voipStatus
        }

        val mic = microphoneRecord
        if (callMicSilencedBySystem) {
            if (voipAssistEnabled) {
                applySpeakerAssist()
            }
            voipStatus = VoipCaptureStatus.SilencedBySystem
            return voipStatus
        }

        if (mic == null) {
            restoreSpeakerAssist()
            voipStatus = VoipCaptureStatus.NoMicrophonePath
            return voipStatus
        }

        if (voipAssistEnabled) {
            applySpeakerAssist()
        } else {
            restoreSpeakerAssist()
        }

        val silenced = isMicrophoneSilencedBySystem(mic)
        voipStatus = when {
            silenced -> VoipCaptureStatus.SilencedBySystem
            speakerAssistApplied -> VoipCaptureStatus.SpeakerAssistActive
            else -> VoipCaptureStatus.CallDetected
        }
        return voipStatus
    }

    fun voipCaptureStatus(): VoipCaptureStatus = voipStatus

    fun isRunning(): Boolean = running

    fun isStalled(
        nowElapsedMs: Long,
        thresholdMs: Long,
    ): Boolean =
        running &&
            !paused &&
            lastWorkerHeartbeatElapsedMs > 0L &&
            nowElapsedMs - lastWorkerHeartbeatElapsedMs >= thresholdMs

    fun stopAfterStall() {
        if (!running) return
        runtimeHealth = AudioCaptureHealth.AllAudioLost
        failure = failure ?: IllegalStateException("Audio capture stalled.")
        abort()
    }

    private fun isCommunicationActive(): Boolean =
        audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audioManager.mode == AudioManager.MODE_IN_CALL

    private fun copyPcm16(
        source: ByteArray,
        byteCount: Int,
        out: ByteArray,
    ): Int {
        val bytes = byteCount.coerceAtLeast(0).and(-2)
        if (bytes <= 0) return 0
        source.copyInto(out, endIndex = bytes)
        return bytes
    }

    private fun isMicrophoneSilencedBySystem(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        return runCatching {
            audioManager.activeRecordingConfigurations
                .firstOrNull { configuration ->
                    configuration.clientAudioSessionId == record.audioSessionId
                }
                ?.isClientSilenced == true
        }.getOrDefault(false)
    }

    private fun applySpeakerAssist() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

            if (speaker != null) {
                if (!speakerAssistApplied) {
                    previousCommunicationDeviceId =
                        audioManager.communicationDevice?.id
                }

                val alreadyOnSpeaker =
                    audioManager.communicationDevice?.id == speaker.id
                speakerAssistApplied = alreadyOnSpeaker || runCatching {
                    audioManager.setCommunicationDevice(speaker)
                }.getOrDefault(false)
            }
            return
        }

        @Suppress("DEPRECATION")
        runCatching {
            if (!speakerAssistApplied) {
                legacySpeakerWasOn = audioManager.isSpeakerphoneOn
            }
            if (!audioManager.isSpeakerphoneOn) {
                audioManager.isSpeakerphoneOn = true
            }
            speakerAssistApplied = audioManager.isSpeakerphoneOn
        }
    }

    private fun restoreSpeakerAssist() {
        if (!speakerAssistApplied) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val previousId = previousCommunicationDeviceId
            val restored = previousId?.let { id ->
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.id == id }
                    ?.let { device ->
                        runCatching {
                            audioManager.setCommunicationDevice(device)
                        }.getOrDefault(false)
                    }
            } == true

            if (!restored) {
                runCatching { audioManager.clearCommunicationDevice() }
            }
            previousCommunicationDeviceId = null
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.isSpeakerphoneOn = legacySpeakerWasOn
            }
        }

        speakerAssistApplied = false
    }

    private fun mixPcm16(
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

    private fun createMicrophoneRecord(
        format: AudioFormat,
        bufferSize: Int,
        preferredMicDeviceId: Int,
        audioSource: Int,
    ): AudioRecord =
        AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .build()
            .also { record ->
                check(record.state == AudioRecord.STATE_INITIALIZED) {
                    "Unable to initialize microphone capture source: $audioSource"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    applyPreferredMicrophoneDevice(
                        record = record,
                        deviceId = preferredMicDeviceId,
                    )
                }
            }

    private fun replaceMicrophoneRecord(
        current: AudioRecord?,
        format: AudioFormat,
        bufferSize: Int,
        preferredMicDeviceId: Int,
        audioSource: Int,
    ): AudioRecord? {
        val replacement = runCatching {
            createMicrophoneRecord(
                format = format,
                bufferSize = bufferSize,
                preferredMicDeviceId = preferredMicDeviceId,
                audioSource = audioSource,
            ).also { it.startRecording() }
        }.getOrNull()

        if (replacement == null) {
            microphoneRecord = current
            return current
        }

        runCatching { current?.stop() }
        runCatching { current?.release() }
        microphoneRecord = replacement
        activeMicSource = audioSource
        return replacement
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun applyPreferredMicrophoneDevice(
        record: AudioRecord,
        deviceId: Int,
    ) {
        if (deviceId < 0) return

        val audioManager = context.getSystemService(AudioManager::class.java)
        val device = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == deviceId }

        if (device != null) {
            runCatching { record.setPreferredDevice(device) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
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
        restoreSpeakerAssist()
        encoder = null
        microphoneRecord = null
        playbackRecord = null
        worker = null
        paused = false
        voipStatus = VoipCaptureStatus.Inactive
        callMicSilencedBySystem = false
        activeMicSource = MediaRecorder.AudioSource.MIC
        if (runtimeHealth == AudioCaptureHealth.Healthy && failure != null) {
            runtimeHealth = AudioCaptureHealth.AllAudioLost
        }
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
        private const val PAUSE_POLL_MS = 20L
        private const val AUDIO_IDLE_BACKOFF_MS = 5L
        private const val CALL_MIC_PROBE_INTERVAL_MS = 1_000L
        private const val CALL_MIC_RECOVERY_INTERVAL_MS = 1_000L
        private const val SPEAKER_ASSIST_REASSERT_INTERVAL_MS = 500L
        private const val ACCESSIBILITY_ASSIST_CHECK_INTERVAL_MS = 1_000L
        private const val AUDIO_FRAME_MS = 20L
        private const val SYNTHETIC_SILENCE_PACE_MS = AUDIO_FRAME_MS
        private const val SYNTHETIC_SILENCE_BYTES =
            SAMPLE_RATE * BYTES_PER_FRAME / 50
        private const val PCM_READ_CHUNK_BYTES = SYNTHETIC_SILENCE_BYTES
        private val CALL_MIC_AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )
        private const val EOS_QUEUE_RETRIES = 100
        private const val EOS_DRAIN_IDLE_LIMIT = 100
    }
}
