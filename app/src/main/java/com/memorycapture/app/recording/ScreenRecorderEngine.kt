package com.memorycapture.app.recording

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import androidx.documentfile.provider.DocumentFile
import com.memorycapture.app.data.preferences.AudioMode
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

data class SavedRecording(
    val displayName: String,
    val uri: Uri?,
    val locationLabel: String,
)

class ScreenRecorderEngine(
    private val context: Context,
) {
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputHandle: OutputHandle? = null
    private var drainThread: Thread? = null
    private var audioCaptureEngine: AudioCaptureEngine? = null

    private val muxerLock = Any()
    private val pendingSamples = ArrayDeque<PendingSample>()

    @Volatile
    private var drainFailure: Throwable? = null

    @Volatile
    private var muxerStarted = false

    @Volatile
    private var abortDrain = false

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var expectedTrackCount = 1
    private var pendingBytes = 0
    private var firstVideoPtsUs = -1L
    private var firstAudioPtsUs = -1L
    private var started = false

    fun start(
        projection: MediaProjection,
        customTreeUri: String? = null,
        customStorageLabel: String? = null,
        audioMode: AudioMode = AudioMode.None,
        preferredMicDeviceId: Int = -1,
    ) {
        check(!started) { "A recording session is already active." }

        val metrics = context.resources.displayMetrics
        val (width, height) = scaledEvenSize(metrics.widthPixels, metrics.heightPixels)
        val output = createOutput(customTreeUri, customStorageLabel)

        var localEncoder: MediaCodec? = null
        var localSurface: Surface? = null
        var localMuxer: MediaMuxer? = null
        var localDisplay: VirtualDisplay? = null
        var localAudio: AudioCaptureEngine? = null

        try {
            resetMuxerState(
                trackCount = if (audioMode == AudioMode.None) 1 else 2,
            )

            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            }

            localEncoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(
                    format,
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE,
                )
            }

            localSurface = localEncoder.createInputSurface()
            localMuxer = createMuxer(output)
            localEncoder.start()

            encoder = localEncoder
            inputSurface = localSurface
            muxer = localMuxer
            outputHandle = output
            drainFailure = null
            abortDrain = false
            started = true

            drainThread = thread(
                start = true,
                name = "MemoryCapture-VideoEncoder",
            ) {
                drainVideoEncoder(localEncoder, localMuxer)
            }

            if (audioMode != AudioMode.None) {
                localAudio = AudioCaptureEngine(context).also { audio ->
                    audio.start(
                        projection = projection,
                        mode = audioMode,
                        preferredMicDeviceId = preferredMicDeviceId,
                        sink = object : AudioMuxerSink {
                            override fun onAudioFormat(format: MediaFormat) {
                                registerTrack(
                                    kind = TrackKind.Audio,
                                    format = format,
                                    activeMuxer = localMuxer,
                                )
                            }

                            override fun onAudioSample(
                                buffer: ByteBuffer,
                                info: MediaCodec.BufferInfo,
                            ) {
                                writeEncodedSample(
                                    kind = TrackKind.Audio,
                                    buffer = buffer,
                                    info = info,
                                    activeMuxer = localMuxer,
                                )
                            }
                        },
                    )
                }
                audioCaptureEngine = localAudio
            }

            localDisplay = projection.createVirtualDisplay(
                "MemoryCapture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                localSurface,
                null,
                null,
            )

            virtualDisplay = localDisplay
        } catch (error: Throwable) {
            started = false
            abortDrain = true
            runCatching { localDisplay?.release() }
            runCatching { localAudio?.abort() }
            runCatching { localSurface?.release() }
            runCatching { localEncoder?.stop() }
            runCatching { localEncoder?.release() }

            synchronized(muxerLock) {
                if (muxerStarted) {
                    runCatching { localMuxer?.stop() }
                }
                runCatching { localMuxer?.release() }
            }

            cleanupFailedOutput(output)
            clearRuntimeState()
            throw error
        }
    }

    fun stopAndSave(): SavedRecording? {
        if (!started) return null

        val activeEncoder = encoder
        val activeSurface = inputSurface
        val activeMuxer = muxer
        val activeDisplay = virtualDisplay
        val activeAudio = audioCaptureEngine
        val output = outputHandle
        val activeDrainThread = drainThread

        started = false

        var stoppedCleanly = true

        try {
            runCatching { activeDisplay?.release() }
                .onFailure { stoppedCleanly = false }

            val signaled = runCatching {
                activeEncoder?.signalEndOfInputStream()
            }.isSuccess
            if (!signaled) stoppedCleanly = false

            activeAudio?.stopAndWait()
            if (activeAudio?.failure != null) {
                stoppedCleanly = false
            }

            activeDrainThread?.join(DRAIN_JOIN_TIMEOUT_MS)

            if (activeDrainThread?.isAlive == true) {
                abortDrain = true
                activeDrainThread.join(DRAIN_ABORT_JOIN_TIMEOUT_MS)
                stoppedCleanly = false
            }

            if (drainFailure != null) {
                stoppedCleanly = false
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            stoppedCleanly = false
        } finally {
            abortDrain = true

            runCatching { activeEncoder?.stop() }
                .onFailure { stoppedCleanly = false }
            runCatching { activeEncoder?.release() }
            runCatching { activeSurface?.release() }

            synchronized(muxerLock) {
                if (muxerStarted) {
                    runCatching { activeMuxer?.stop() }
                        .onFailure { stoppedCleanly = false }
                } else {
                    stoppedCleanly = false
                }
                runCatching { activeMuxer?.release() }
            }

            runCatching { output?.fileDescriptor?.close() }

            clearRuntimeState()
        }

        if (output == null) return null

        if (!stoppedCleanly) {
            cleanupFailedOutput(output)
            return null
        }

        finalizeOutput(output)

        return SavedRecording(
            displayName = output.displayName,
            uri = output.uri,
            locationLabel = output.locationLabel,
        )
    }

    fun abort() {
        val activeEncoder = encoder
        val activeSurface = inputSurface
        val activeMuxer = muxer
        val activeDisplay = virtualDisplay
        val activeAudio = audioCaptureEngine
        val output = outputHandle
        val activeDrainThread = drainThread

        started = false
        abortDrain = true

        runCatching { activeDisplay?.release() }
        runCatching { activeAudio?.abort() }

        try {
            activeDrainThread?.join(DRAIN_ABORT_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        runCatching { activeEncoder?.stop() }
        runCatching { activeEncoder?.release() }
        runCatching { activeSurface?.release() }

        synchronized(muxerLock) {
            if (muxerStarted) {
                runCatching { activeMuxer?.stop() }
            }
            runCatching { activeMuxer?.release() }
        }

        runCatching { output?.fileDescriptor?.close() }

        if (output != null) {
            cleanupFailedOutput(output)
        }

        clearRuntimeState()
    }

    fun isActive(): Boolean = started

    private fun drainVideoEncoder(
        codec: MediaCodec,
        activeMuxer: MediaMuxer,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (!abortDrain) {
                when (val outputBufferIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    DEQUEUE_TIMEOUT_US,
                )) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        registerTrack(
                            kind = TrackKind.Video,
                            format = codec.outputFormat,
                            activeMuxer = activeMuxer,
                        )
                    }

                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedData = codec.getOutputBuffer(outputBufferIndex)
                                ?: error("Video encoder returned a null output buffer.")

                            if (
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            ) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size > 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)

                                writeEncodedSample(
                                    kind = TrackKind.Video,
                                    buffer = encodedData,
                                    info = bufferInfo,
                                    activeMuxer = activeMuxer,
                                )
                            }

                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if (
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            ) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            drainFailure = error
        }
    }

    private fun registerTrack(
        kind: TrackKind,
        format: MediaFormat,
        activeMuxer: MediaMuxer,
    ) {
        synchronized(muxerLock) {
            when (kind) {
                TrackKind.Video -> {
                    if (videoTrackIndex >= 0) return
                    videoTrackIndex = activeMuxer.addTrack(format)
                }

                TrackKind.Audio -> {
                    if (audioTrackIndex >= 0) return
                    audioTrackIndex = activeMuxer.addTrack(format)
                }
            }

            val readyTracks =
                (if (videoTrackIndex >= 0) 1 else 0) +
                    (if (audioTrackIndex >= 0) 1 else 0)

            if (!muxerStarted && readyTracks == expectedTrackCount) {
                activeMuxer.start()
                muxerStarted = true
                flushPendingSamples(activeMuxer)
            }
        }
    }

    private fun writeEncodedSample(
        kind: TrackKind,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        activeMuxer: MediaMuxer,
    ) {
        if (info.size <= 0) return

        synchronized(muxerLock) {
            val normalizedPts = normalizedPresentationTime(kind, info.presentationTimeUs)

            if (muxerStarted) {
                val trackIndex = trackIndex(kind)
                check(trackIndex >= 0) {
                    "Encoded sample arrived before its muxer track was registered."
                }
                val adjusted = MediaCodec.BufferInfo().apply {
                    set(
                        info.offset,
                        info.size,
                        normalizedPts,
                        info.flags,
                    )
                }
                activeMuxer.writeSampleData(trackIndex, buffer, adjusted)
                return
            }

            val copy = ByteArray(info.size)
            val duplicate = buffer.duplicate().apply {
                position(info.offset)
                limit(info.offset + info.size)
            }
            duplicate.get(copy)

            pendingBytes += copy.size
            check(pendingBytes <= MAX_PENDING_BYTES) {
                "Audio/video muxer preparation exceeded the pending buffer limit."
            }

            pendingSamples.addLast(
                PendingSample(
                    kind = kind,
                    data = copy,
                    presentationTimeUs = normalizedPts,
                    flags = info.flags,
                ),
            )
        }
    }

    private fun flushPendingSamples(activeMuxer: MediaMuxer) {
        while (pendingSamples.isNotEmpty()) {
            val pending = pendingSamples.removeFirst()
            val info = MediaCodec.BufferInfo().apply {
                set(
                    0,
                    pending.data.size,
                    pending.presentationTimeUs,
                    pending.flags,
                )
            }
            activeMuxer.writeSampleData(
                trackIndex(pending.kind),
                ByteBuffer.wrap(pending.data),
                info,
            )
        }
        pendingBytes = 0
    }

    private fun trackIndex(kind: TrackKind): Int =
        when (kind) {
            TrackKind.Video -> videoTrackIndex
            TrackKind.Audio -> audioTrackIndex
        }

    private fun normalizedPresentationTime(
        kind: TrackKind,
        presentationTimeUs: Long,
    ): Long {
        val first = when (kind) {
            TrackKind.Video -> {
                if (firstVideoPtsUs < 0L) firstVideoPtsUs = presentationTimeUs
                firstVideoPtsUs
            }
            TrackKind.Audio -> {
                if (firstAudioPtsUs < 0L) firstAudioPtsUs = presentationTimeUs
                firstAudioPtsUs
            }
        }
        return (presentationTimeUs - first).coerceAtLeast(0L)
    }

    private fun resetMuxerState(trackCount: Int) {
        synchronized(muxerLock) {
            expectedTrackCount = trackCount
            videoTrackIndex = -1
            audioTrackIndex = -1
            muxerStarted = false
            pendingSamples.clear()
            pendingBytes = 0
            firstVideoPtsUs = -1L
            firstAudioPtsUs = -1L
        }
    }

    private fun createMuxer(output: OutputHandle): MediaMuxer =
        if (output.fileDescriptor != null) {
            MediaMuxer(
                output.fileDescriptor.fileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
        } else {
            MediaMuxer(
                requireNotNull(output.absolutePath),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
        }

    private fun createOutput(
        customTreeUri: String?,
        customStorageLabel: String?,
    ): OutputHandle {
        val displayName =
            "MemoryCapture_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                ".mp4"

        if (!customTreeUri.isNullOrBlank()) {
            val treeUri = Uri.parse(customTreeUri)
            val tree = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
                "Unable to access selected storage folder."
            }

            val document = requireNotNull(
                tree.createFile("video/mp4", displayName),
            ) {
                "Unable to create video in selected folder."
            }

            val pfd = requireNotNull(
                context.contentResolver.openFileDescriptor(document.uri, "rw"),
            ) {
                "Unable to open selected folder output file."
            }

            return OutputHandle(
                displayName = displayName,
                uri = document.uri,
                fileDescriptor = pfd,
                absolutePath = null,
                locationLabel = customStorageLabel
                    ?.takeIf { it.isNotBlank() }
                    ?: "Selected folder",
                pendingMediaStoreItem = false,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/MemoryCapture",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = requireNotNull(
                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values,
                ),
            ) {
                "Unable to create MediaStore video entry."
            }

            val pfd = requireNotNull(
                context.contentResolver.openFileDescriptor(uri, "rw"),
            ) {
                "Unable to open MediaStore output file."
            }

            return OutputHandle(
                displayName = displayName,
                uri = uri,
                fileDescriptor = pfd,
                absolutePath = null,
                locationLabel = Environment.DIRECTORY_MOVIES + "/MemoryCapture",
                pendingMediaStoreItem = true,
            )
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val directory = File(root, "MemoryCapture").apply { mkdirs() }
        val file = File(directory, displayName)

        return OutputHandle(
            displayName = displayName,
            uri = null,
            fileDescriptor = null,
            absolutePath = file.absolutePath,
            locationLabel = file.parentFile?.absolutePath ?: file.absolutePath,
            pendingMediaStoreItem = false,
        )
    }

    private fun finalizeOutput(output: OutputHandle) {
        if (output.pendingMediaStoreItem && output.uri != null) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(output.uri, values, null, null)
            return
        }

        output.absolutePath?.let { path ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf("video/mp4"),
                null,
            )
        }
    }

    private fun cleanupFailedOutput(output: OutputHandle) {
        runCatching { output.fileDescriptor?.close() }

        if (output.uri != null) {
            runCatching {
                context.contentResolver.delete(output.uri, null, null)
            }
        } else {
            output.absolutePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    private fun scaledEvenSize(
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int> {
        val longest = maxOf(sourceWidth, sourceHeight)
        val scale = if (longest > MAX_LONG_EDGE) {
            MAX_LONG_EDGE.toFloat() / longest.toFloat()
        } else {
            1f
        }

        var width = (sourceWidth * scale).toInt().coerceAtLeast(2)
        var height = (sourceHeight * scale).toInt().coerceAtLeast(2)

        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1

        return width to height
    }

    private fun clearRuntimeState() {
        encoder = null
        inputSurface = null
        muxer = null
        virtualDisplay = null
        outputHandle = null
        drainThread = null
        audioCaptureEngine = null
        drainFailure = null
        abortDrain = false
        resetMuxerState(1)
    }

    private enum class TrackKind {
        Video,
        Audio,
    }

    private data class PendingSample(
        val kind: TrackKind,
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
    )

    private data class OutputHandle(
        val displayName: String,
        val uri: Uri?,
        val fileDescriptor: ParcelFileDescriptor?,
        val absolutePath: String?,
        val locationLabel: String,
        val pendingMediaStoreItem: Boolean,
    )

    companion object {
        private const val MIME_TYPE = "video/avc"
        private const val VIDEO_BIT_RATE = 8_000_000
        private const val VIDEO_FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val MAX_LONG_EDGE = 1920
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 8_000L
        private const val DRAIN_ABORT_JOIN_TIMEOUT_MS = 1_500L
        private const val MAX_PENDING_BYTES = 16 * 1024 * 1024
    }
}
