package com.memorycapture.app.recording

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import androidx.documentfile.provider.DocumentFile
import com.memorycapture.app.data.preferences.AudioMode
import com.memorycapture.app.data.preferences.RecordingFrameRate
import com.memorycapture.app.data.preferences.RecordingQuality
import com.memorycapture.app.data.preferences.VideoBitratePreset
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class SavedRecording(
    val displayName: String,
    val uri: Uri?,
    val locationLabel: String,
)

data class SavedScreenshot(
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
    private var totalVideoPausedUs = 0L
    private var pauseStartedNs = 0L
    private var started = false
    private var paused = false
    private var activeCaptureWidth = 0
    private var activeCaptureHeight = 0

    fun start(
        projection: MediaProjection,
        customTreeUri: String? = null,
        customStorageLabel: String? = null,
        audioMode: AudioMode = AudioMode.None,
        preferredMicDeviceId: Int = -1,
        quality: RecordingQuality = RecordingQuality.P1080,
        frameRate: RecordingFrameRate = RecordingFrameRate.Fps30,
        bitratePreset: VideoBitratePreset = VideoBitratePreset.Balanced,
    ) {
        check(!started) { "A recording session is already active." }

        val metrics = context.resources.displayMetrics
        val sourceWidth = metrics.widthPixels
        val sourceHeight = metrics.heightPixels
        val preferredSize = scaledEvenSizeForQuality(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            quality = quality,
        )
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

            val preferredProfile = EncoderProfile(
                width = preferredSize.first,
                height = preferredSize.second,
                frameRate = frameRate.fps,
                bitRate = calculateVideoBitrate(
                    width = preferredSize.first,
                    height = preferredSize.second,
                    frameRate = frameRate.fps,
                    preset = bitratePreset,
                ),
            )
            val fallbackSize = scaledEvenSizeForQuality(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                quality = RecordingQuality.P1080,
            )
            val fallbackProfile = EncoderProfile(
                width = fallbackSize.first,
                height = fallbackSize.second,
                frameRate = RecordingFrameRate.Fps30.fps,
                bitRate = calculateVideoBitrate(
                    width = fallbackSize.first,
                    height = fallbackSize.second,
                    frameRate = RecordingFrameRate.Fps30.fps,
                    preset = VideoBitratePreset.Balanced,
                ),
            )

            val configured = createConfiguredVideoEncoder(
                preferred = preferredProfile,
                fallback = fallbackProfile,
            )
            localEncoder = configured.codec
            val activeProfile = configured.profile
            localSurface = localEncoder.createInputSurface()
            localMuxer = createMuxer(output)
            localEncoder.start()

            encoder = localEncoder
            inputSurface = localSurface
            muxer = localMuxer
            outputHandle = output
            drainFailure = null
            abortDrain = false
            paused = false
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
                activeProfile.width,
                activeProfile.height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                localSurface,
                null,
                null,
            )

            virtualDisplay = localDisplay
            activeCaptureWidth = activeProfile.width
            activeCaptureHeight = activeProfile.height
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

    fun pause() {
        if (!started || paused) return

        virtualDisplay?.surface = null
        audioCaptureEngine?.pause()
        pauseStartedNs = System.nanoTime()
        paused = true
    }

    fun resume() {
        if (!started || !paused) return

        val pausedUs = if (pauseStartedNs > 0L) {
            (System.nanoTime() - pauseStartedNs).coerceAtLeast(0L) / 1_000L
        } else {
            0L
        }

        virtualDisplay?.surface = inputSurface
        audioCaptureEngine?.resume()
        totalVideoPausedUs += pausedUs
        pauseStartedNs = 0L
        paused = false
    }

    @Synchronized
    fun captureScreenshot(): SavedScreenshot? {
        if (!started || paused) return null

        val display = virtualDisplay ?: return null
        val encoderSurface = inputSurface ?: return null
        val width = activeCaptureWidth
        val height = activeCaptureHeight
        if (width <= 0 || height <= 0) return null

        val imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
        )
        val handlerThread = HandlerThread("MemoryCapture-Screenshot").apply { start() }
        val handler = Handler(handlerThread.looper)
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride

                val padded = Bitmap.createBitmap(
                    paddedWidth,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                padded.copyPixelsFromBuffer(buffer)
                bitmap = if (paddedWidth == width) {
                    padded
                } else {
                    Bitmap.createBitmap(padded, 0, 0, width, height).also {
                        padded.recycle()
                    }
                }
            } finally {
                image.close()
                latch.countDown()
            }
        }, handler)

        return try {
            display.surface = imageReader.surface
            val captured = latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            display.surface = encoderSurface

            if (!captured) return null
            val imageBitmap = bitmap ?: return null
            try {
                saveScreenshotBitmap(imageBitmap)
            } finally {
                imageBitmap.recycle()
            }
        } catch (_: Throwable) {
            runCatching { display.surface = encoderSurface }
            null
        } finally {
            imageReader.setOnImageAvailableListener(null, null)
            imageReader.close()
            handlerThread.quitSafely()
        }
    }

    fun stopAndSave(): SavedRecording? {
        if (!started) return null

        if (paused) {
            val pausedUs = if (pauseStartedNs > 0L) {
                (System.nanoTime() - pauseStartedNs).coerceAtLeast(0L) / 1_000L
            } else {
                0L
            }
            totalVideoPausedUs += pausedUs
            pauseStartedNs = 0L
            paused = false
        }

        val activeEncoder = encoder
        val activeSurface = inputSurface
        val activeMuxer = muxer
        val activeDisplay = virtualDisplay
        val activeAudio = audioCaptureEngine
        val output = outputHandle
        val activeDrainThread = drainThread

        started = false
        paused = false

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
        paused = false
        pauseStartedNs = 0L
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
                val base = firstVideoPtsUs
                return (presentationTimeUs - base - totalVideoPausedUs)
                    .coerceAtLeast(0L)
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
            totalVideoPausedUs = 0L
            pauseStartedNs = 0L
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

    private fun saveScreenshotBitmap(bitmap: Bitmap): SavedScreenshot? {
        val displayName =
            "MemoryCapture_" +
                SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) +
                ".png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/MemoryCapture",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return null

            val written = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } == true
            }.getOrDefault(false)

            if (!written) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }

            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )

            return SavedScreenshot(
                displayName = displayName,
                uri = uri,
                locationLabel = Environment.DIRECTORY_PICTURES + "/MemoryCapture",
            )
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val directory = File(root, "MemoryCapture").apply { mkdirs() }
        val file = File(directory, displayName)

        val written = runCatching {
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }.getOrDefault(false)

        if (!written) {
            runCatching { file.delete() }
            return null
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/png"),
            null,
        )

        return SavedScreenshot(
            displayName = displayName,
            uri = null,
            locationLabel = file.parentFile?.absolutePath ?: file.absolutePath,
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

    private fun scaledEvenSizeForQuality(
        sourceWidth: Int,
        sourceHeight: Int,
        quality: RecordingQuality,
    ): Pair<Int, Int> {
        val requestedLongEdge = when (quality) {
            RecordingQuality.Auto -> minOf(maxOf(sourceWidth, sourceHeight), 1920)
            RecordingQuality.P720 -> 1280
            RecordingQuality.P1080 -> 1920
            RecordingQuality.P1440 -> 2560
        }
        val longest = maxOf(sourceWidth, sourceHeight)
        val targetLongEdge = minOf(longest, requestedLongEdge)
        val scale = targetLongEdge.toFloat() / longest.toFloat()

        var width = (sourceWidth * scale).toInt().coerceAtLeast(2)
        var height = (sourceHeight * scale).toInt().coerceAtLeast(2)

        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1

        return width to height
    }

    private fun calculateVideoBitrate(
        width: Int,
        height: Int,
        frameRate: Int,
        preset: VideoBitratePreset,
    ): Int {
        val pixelsPerSecond = width.toLong() * height.toLong() * frameRate.toLong()
        val bitsPerPixel = when (preset) {
            VideoBitratePreset.Efficient -> 0.055
            VideoBitratePreset.Balanced -> 0.085
            VideoBitratePreset.High -> 0.12
        }
        return (pixelsPerSecond * bitsPerPixel)
            .toLong()
            .coerceIn(MIN_VIDEO_BIT_RATE.toLong(), MAX_VIDEO_BIT_RATE.toLong())
            .toInt()
    }

    private fun createConfiguredVideoEncoder(
        preferred: EncoderProfile,
        fallback: EncoderProfile,
    ): ConfiguredEncoder {
        val attempts = if (preferred == fallback) {
            listOf(preferred)
        } else {
            listOf(preferred, fallback)
        }

        var lastError: Throwable? = null
        attempts.forEach { profile ->
            val codec = runCatching { MediaCodec.createEncoderByType(MIME_TYPE) }
                .getOrElse {
                    lastError = it
                    return@forEach
                }
            val format = MediaFormat.createVideoFormat(
                MIME_TYPE,
                profile.width,
                profile.height,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, profile.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, profile.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            }

            val configured = runCatching {
                codec.configure(
                    format,
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE,
                )
            }
            if (configured.isSuccess) {
                return ConfiguredEncoder(codec = codec, profile = profile)
            }

            lastError = configured.exceptionOrNull()
            runCatching { codec.release() }
        }

        throw lastError ?: IllegalStateException("Unable to configure video encoder.")
    }

    private fun clearRuntimeState() {
        encoder = null
        inputSurface = null
        muxer = null
        virtualDisplay = null
        outputHandle = null
        drainThread = null
        audioCaptureEngine = null
        activeCaptureWidth = 0
        activeCaptureHeight = 0
        drainFailure = null
        abortDrain = false
        paused = false
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

    private data class EncoderProfile(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val bitRate: Int,
    )

    private data class ConfiguredEncoder(
        val codec: MediaCodec,
        val profile: EncoderProfile,
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
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val MIN_VIDEO_BIT_RATE = 2_500_000
        private const val MAX_VIDEO_BIT_RATE = 28_000_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 8_000L
        private const val DRAIN_ABORT_JOIN_TIMEOUT_MS = 1_500L
        private const val SCREENSHOT_TIMEOUT_MS = 1_500L
        private const val MAX_PENDING_BYTES = 16 * 1024 * 1024
    }
}
