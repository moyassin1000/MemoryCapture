package com.memorycapture.app.recording

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

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

    @Volatile private var drainFailure: Throwable? = null
    @Volatile private var muxerStarted = false
    @Volatile private var abortDrain = false
    @Volatile private var encodedFrames = 0L

    private var started = false

    fun start(
        projection: MediaProjection,
        customTreeUri: String? = null,
        customStorageLabel: String? = null,
    ) {
        check(!started) { "A recording session is already active." }

        RecorderDiagnosticsStore.reset()

        val metrics = context.resources.displayMetrics
        val candidate = chooseEncoder(metrics.widthPixels, metrics.heightPixels)
        val output = createOutput(customTreeUri, customStorageLabel)

        var localEncoder: MediaCodec? = null
        var localSurface: Surface? = null
        var localMuxer: MediaMuxer? = null
        var localDisplay: VirtualDisplay? = null

        try {
            val format = MediaFormat.createVideoFormat(
                MIME_TYPE,
                candidate.width,
                candidate.height,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, candidate.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            }

            localEncoder = MediaCodec.createByCodecName(candidate.codecName).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            localSurface = localEncoder.createInputSurface()
            localMuxer = createMuxer(output)
            localEncoder.start()

            localDisplay = projection.createVirtualDisplay(
                "MemoryCapture",
                candidate.width,
                candidate.height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                localSurface,
                null,
                null,
            )

            encoder = localEncoder
            inputSurface = localSurface
            muxer = localMuxer
            virtualDisplay = localDisplay
            outputHandle = output

            drainFailure = null
            muxerStarted = false
            abortDrain = false
            encodedFrames = 0L
            started = true

            RecorderDiagnosticsStore.starting(
                codecName = candidate.codecName,
                width = candidate.width,
                height = candidate.height,
                frameRate = VIDEO_FRAME_RATE,
                bitRate = candidate.bitRate,
            )

            drainThread = thread(
                start = true,
                name = "MemoryCapture-VideoEncoder",
            ) {
                drainEncoder(localEncoder, localMuxer)
            }
        } catch (error: Throwable) {
            RecorderDiagnosticsStore.error(error.message ?: "Unable to start video encoder")
            runCatching { localDisplay?.release() }
            runCatching { localSurface?.release() }
            runCatching { localEncoder?.stop() }
            runCatching { localEncoder?.release() }
            runCatching { localMuxer?.release() }
            cleanupFailedOutput(output)
            clearRuntimeState(resetDiagnostics = false)
            throw error
        }
    }

    fun stopAndSave(): SavedRecording? {
        if (!started) return null

        val activeEncoder = encoder
        val activeSurface = inputSurface
        val activeMuxer = muxer
        val activeDisplay = virtualDisplay
        val output = outputHandle
        val activeDrainThread = drainThread

        started = false
        var stoppedCleanly = true

        try {
            runCatching { activeDisplay?.release() }.onFailure { stoppedCleanly = false }

            if (runCatching { activeEncoder?.signalEndOfInputStream() }.isFailure) {
                stoppedCleanly = false
            }

            activeDrainThread?.join(DRAIN_JOIN_TIMEOUT_MS)

            if (activeDrainThread?.isAlive == true) {
                abortDrain = true
                activeDrainThread.join(DRAIN_ABORT_JOIN_TIMEOUT_MS)
                stoppedCleanly = false
            }

            if (drainFailure != null || encodedFrames < MIN_VALID_FRAMES) {
                stoppedCleanly = false
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            stoppedCleanly = false
        } finally {
            abortDrain = true
            runCatching { activeEncoder?.stop() }.onFailure { stoppedCleanly = false }
            runCatching { activeEncoder?.release() }
            runCatching { activeSurface?.release() }

            if (muxerStarted) {
                runCatching { activeMuxer?.stop() }.onFailure { stoppedCleanly = false }
            } else {
                stoppedCleanly = false
            }

            runCatching { activeMuxer?.release() }
            runCatching { output?.fileDescriptor?.close() }
            clearRuntimeState(resetDiagnostics = false)
        }

        if (output == null) return null

        if (!stoppedCleanly) {
            RecorderDiagnosticsStore.error(
                if (encodedFrames < MIN_VALID_FRAMES) {
                    "Too few video frames were encoded."
                } else {
                    drainFailure?.message ?: "Video finalization failed."
                },
            )
            cleanupFailedOutput(output)
            return null
        }

        finalizeOutput(output)
        RecorderDiagnosticsStore.stopped()

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
        val output = outputHandle
        val activeDrainThread = drainThread

        started = false
        abortDrain = true
        runCatching { activeDisplay?.release() }

        try {
            activeDrainThread?.join(DRAIN_ABORT_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        runCatching { activeEncoder?.stop() }
        runCatching { activeEncoder?.release() }
        runCatching { activeSurface?.release() }
        if (muxerStarted) runCatching { activeMuxer?.stop() }
        runCatching { activeMuxer?.release() }
        runCatching { output?.fileDescriptor?.close() }

        if (output != null) cleanupFailedOutput(output)
        clearRuntimeState(resetDiagnostics = false)
    }

    fun isActive(): Boolean = started

    private fun chooseEncoder(sourceWidth: Int, sourceHeight: Int): EncoderCandidate {
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE, ignoreCase = true) }
            }
            .sortedWith(
                compareByDescending<MediaCodecInfo> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.isHardwareAccelerated else true
                }.thenBy { it.name },
            )

        val requestedSizes = listOf(1920, 1600, 1280, 960)

        for (maxLongEdge in requestedSizes) {
            val (baseWidth, baseHeight) = scaledSize(
                sourceWidth,
                sourceHeight,
                maxLongEdge,
            )

            for (info in codecInfos) {
                val capabilities = runCatching {
                    info.getCapabilitiesForType(MIME_TYPE)
                }.getOrNull() ?: continue

                if (
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface !in
                    capabilities.colorFormats
                ) {
                    continue
                }

                val video = capabilities.videoCapabilities ?: continue
                val width = alignDown(baseWidth, video.widthAlignment)
                val height = alignDown(baseHeight, video.heightAlignment)

                val supported = runCatching {
                    video.isSizeSupported(width, height) &&
                        video.areSizeAndRateSupported(
                            width,
                            height,
                            VIDEO_FRAME_RATE.toDouble(),
                        )
                }.getOrDefault(false)

                if (!supported) continue

                return EncoderCandidate(
                    codecName = info.name,
                    width = width,
                    height = height,
                    bitRate = calculateBitRate(width, height),
                )
            }
        }

        error("No compatible H.264 screen encoder was found on this device.")
    }

    private fun drainEncoder(
        codec: MediaCodec,
        activeMuxer: MediaMuxer,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var videoTrackIndex = -1

        try {
            while (!abortDrain) {
                when (
                    val outputBufferIndex = codec.dequeueOutputBuffer(
                        bufferInfo,
                        DEQUEUE_TIMEOUT_US,
                    )
                ) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) {
                            "The video encoder output format changed more than once."
                        }
                        videoTrackIndex = activeMuxer.addTrack(codec.outputFormat)
                        activeMuxer.start()
                        muxerStarted = true
                    }

                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedData = codec.getOutputBuffer(outputBufferIndex)
                                ?: error("Video encoder returned a null output buffer.")

                            if (
                                bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            ) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size > 0) {
                                check(muxerStarted && videoTrackIndex >= 0) {
                                    "Encoded video arrived before MediaMuxer was ready."
                                }

                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                activeMuxer.writeSampleData(
                                    videoTrackIndex,
                                    encodedData,
                                    bufferInfo,
                                )

                                encodedFrames += 1L
                                RecorderDiagnosticsStore.onEncodedFrame(
                                    count = encodedFrames,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                )
                            }

                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if (
                                bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            ) {
                                break
                            }
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            drainFailure = error
            RecorderDiagnosticsStore.error(error.message ?: "Video encoder drain failed.")
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
        val displayName = "MemoryCapture_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.mp4"

        if (!customTreeUri.isNullOrBlank()) {
            val treeUri = Uri.parse(customTreeUri)
            val tree = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
                "Unable to access selected storage folder."
            }
            val document = requireNotNull(tree.createFile("video/mp4", displayName)) {
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
                    "${Environment.DIRECTORY_MOVIES}/MemoryCapture",
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
                locationLabel = "${Environment.DIRECTORY_MOVIES}/MemoryCapture",
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
            context.contentResolver.update(
                output.uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
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

    private fun scaledSize(
        sourceWidth: Int,
        sourceHeight: Int,
        maxLongEdge: Int,
    ): Pair<Int, Int> {
        val longest = max(sourceWidth, sourceHeight)
        val scale = if (longest > maxLongEdge) {
            maxLongEdge.toFloat() / longest.toFloat()
        } else {
            1f
        }

        return Pair(
            (sourceWidth * scale).toInt().coerceAtLeast(16),
            (sourceHeight * scale).toInt().coerceAtLeast(16),
        )
    }

    private fun alignDown(value: Int, alignment: Int): Int =
        (value / alignment * alignment).coerceAtLeast(alignment)

    private fun calculateBitRate(width: Int, height: Int): Int {
        val pixelsPerSecond = width.toLong() * height.toLong() * VIDEO_FRAME_RATE
        return (pixelsPerSecond * BITS_PER_PIXEL).toInt()
            .coerceIn(MIN_BIT_RATE, MAX_BIT_RATE)
    }

    private fun clearRuntimeState(resetDiagnostics: Boolean) {
        encoder = null
        inputSurface = null
        muxer = null
        virtualDisplay = null
        outputHandle = null
        drainThread = null
        drainFailure = null
        muxerStarted = false
        abortDrain = false
        encodedFrames = 0L
        if (resetDiagnostics) RecorderDiagnosticsStore.reset()
    }

    private data class EncoderCandidate(
        val codecName: String,
        val width: Int,
        val height: Int,
        val bitRate: Int,
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
        private const val VIDEO_FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val BITS_PER_PIXEL = 0.10
        private const val MIN_BIT_RATE = 3_000_000
        private const val MAX_BIT_RATE = 14_000_000
        private const val MIN_VALID_FRAMES = 2L
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 8_000L
        private const val DRAIN_ABORT_JOIN_TIMEOUT_MS = 1_500L
    }
}
