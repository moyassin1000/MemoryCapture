package com.memorycapture.app.recording

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedRecording(
    val displayName: String,
    val uri: Uri?,
    val locationLabel: String,
)

class ScreenRecorderEngine(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputHandle: OutputHandle? = null
    private var started = false

    fun start(
        projection: MediaProjection,
        customTreeUri: String? = null,
        customStorageLabel: String? = null,
    ) {
        check(!started) { "A recording session is already active." }

        val metrics = context.resources.displayMetrics
        val (width, height) = scaledEvenSize(metrics.widthPixels, metrics.heightPixels)
        val output = createOutput(customTreeUri, customStorageLabel)

        try {
            val mediaRecorder = createMediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(8_000_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)

                if (output.fileDescriptor != null) {
                    setOutputFile(output.fileDescriptor.fileDescriptor)
                } else {
                    setOutputFile(requireNotNull(output.absolutePath))
                }

                prepare()
            }

            val display = projection.createVirtualDisplay(
                "MemoryCapture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface,
                null,
                null,
            )

            mediaRecorder.start()

            recorder = mediaRecorder
            virtualDisplay = display
            outputHandle = output
            started = true
        } catch (error: Throwable) {
            cleanupFailedOutput(output)
            throw error
        }
    }

    fun stopAndSave(): SavedRecording? {
        if (!started) return null

        val activeRecorder = recorder
        val activeDisplay = virtualDisplay
        val output = outputHandle

        started = false
        recorder = null
        virtualDisplay = null
        outputHandle = null

        var stoppedCleanly = false
        try {
            activeRecorder?.stop()
            stoppedCleanly = true
        } catch (_: RuntimeException) {
            stoppedCleanly = false
        } finally {
            activeDisplay?.release()
            runCatching { activeRecorder?.reset() }
            activeRecorder?.release()
            runCatching { output?.fileDescriptor?.close() }
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
        val activeRecorder = recorder
        val activeDisplay = virtualDisplay
        val output = outputHandle

        started = false
        recorder = null
        virtualDisplay = null
        outputHandle = null

        activeDisplay?.release()
        runCatching { activeRecorder?.reset() }
        activeRecorder?.release()
        runCatching { output?.fileDescriptor?.close() }
        if (output != null) cleanupFailedOutput(output)
    }

    fun isActive(): Boolean = started

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
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
                context.contentResolver.openFileDescriptor(document.uri, "w"),
            ) { "Unable to open selected folder output file." }

            return OutputHandle(
                displayName = displayName,
                uri = document.uri,
                fileDescriptor = pfd,
                absolutePath = null,
                locationLabel = customStorageLabel?.takeIf { it.isNotBlank() } ?: "Selected folder",
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
            ) { "Unable to create MediaStore video entry." }

            val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "w")) {
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
            runCatching { context.contentResolver.delete(output.uri, null, null) }
        } else {
            output.absolutePath?.let { runCatching { File(it).delete() } }
        }
    }

    private fun scaledEvenSize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
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

    private data class OutputHandle(
        val displayName: String,
        val uri: Uri?,
        val fileDescriptor: ParcelFileDescriptor?,
        val absolutePath: String?,
        val locationLabel: String,
        val pendingMediaStoreItem: Boolean,
    )

    companion object {
        private const val MAX_LONG_EDGE = 1920
    }
}
