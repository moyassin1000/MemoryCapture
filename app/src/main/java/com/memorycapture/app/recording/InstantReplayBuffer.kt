package com.memorycapture.app.recording

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayDeque

enum class ReplayTrackKind(val id: Int) {
    Video(0),
    Audio(1);

    companion object {
        fun fromId(id: Int): ReplayTrackKind =
            entries.firstOrNull { it.id == id }
                ?: error("Unknown replay track id: $id")
    }
}

class InstantReplayBuffer(
    cacheDirectory: File,
    private val maxDurationUs: Long,
) {
    private val directory = File(cacheDirectory, "instant_replay").apply {
        mkdirs()
        listFiles()?.forEach { runCatching { it.delete() } }
    }

    private val segments = ArrayDeque<ReplaySegment>()
    private val pinnedFiles = mutableSetOf<String>()
    private var current: ReplaySegmentWriter? = null
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var latestPtsUs = 0L
    private var sequence = 0
    private var disposed = false

    @Synchronized
    fun registerFormat(
        kind: ReplayTrackKind,
        format: MediaFormat,
    ) {
        if (disposed) return
        when (kind) {
            ReplayTrackKind.Video -> videoFormat = format
            ReplayTrackKind.Audio -> audioFormat = format
        }
    }

    @Synchronized
    fun append(
        kind: ReplayTrackKind,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        normalizedPtsUs: Long,
    ) {
        if (disposed || info.size <= 0) return

        val writer = current ?: openSegment(normalizedPtsUs)
        if (
            normalizedPtsUs - writer.startPtsUs >= SEGMENT_DURATION_US &&
            writer.sampleCount > 0
        ) {
            sealCurrent()
        }

        val active = current ?: openSegment(normalizedPtsUs)
        val duplicate = buffer.duplicate().apply {
            position(info.offset)
            limit(info.offset + info.size)
        }
        val bytes = ByteArray(info.size)
        duplicate.get(bytes)

        active.write(
            kind = kind,
            ptsUs = normalizedPtsUs,
            flags = info.flags,
            bytes = bytes,
        )
        latestPtsUs = maxOf(latestPtsUs, normalizedPtsUs)
        prune()
    }

    fun export(
        outputPath: String,
        durationUs: Long,
    ): Boolean {
        val snapshot = synchronized(this) {
            if (disposed) return false

            sealCurrent()
            if (segments.isEmpty()) return false

            val video = videoFormat ?: return false
            val files = segments.map { it.file }
            files.forEach { pinnedFiles += it.absolutePath }

            ExportSnapshot(
                files = files,
                videoFormat = video,
                audioFormat = audioFormat,
                latestPtsUs = latestPtsUs,
            )
        }

        return try {
            exportSnapshot(
                snapshot = snapshot,
                outputPath = outputPath,
                durationUs = durationUs,
            )
        } finally {
            synchronized(this) {
                snapshot.files.forEach { pinnedFiles -= it.absolutePath }
                if (disposed) {
                    snapshot.files.forEach { runCatching { it.delete() } }
                    cleanupDirectoryIfDisposed()
                } else {
                    prune()
                }
            }
        }
    }

    @Synchronized
    fun maintenance() {
        if (disposed) return

        prune()

        val activePaths = buildSet {
            current?.file?.absolutePath?.let(::add)
            segments.forEach { add(it.file.absolutePath) }
            addAll(pinnedFiles)
        }
        val cutoff = System.currentTimeMillis() - ORPHAN_SEGMENT_GRACE_MS

        directory.listFiles()?.forEach { file ->
            if (
                file.absolutePath !in activePaths &&
                file.lastModified() in 1 until cutoff
            ) {
                runCatching { file.delete() }
            }
        }
    }

    @Synchronized
    fun clear() {
        disposed = true

        runCatching { current?.closeAndBuild() }
        current = null

        segments.forEach { segment ->
            if (segment.file.absolutePath !in pinnedFiles) {
                runCatching { segment.file.delete() }
            }
        }
        segments.clear()

        directory.listFiles()?.forEach { file ->
            if (file.absolutePath !in pinnedFiles) {
                runCatching { file.delete() }
            }
        }

        videoFormat = null
        audioFormat = null
        latestPtsUs = 0L

        cleanupDirectoryIfDisposed()
    }

    private fun exportSnapshot(
        snapshot: ExportSnapshot,
        outputPath: String,
        durationUs: Long,
    ): Boolean {
        val desiredStartUs =
            (snapshot.latestPtsUs - durationUs).coerceAtLeast(0L)
        val startUs = findReplayStartUs(
            files = snapshot.files,
            desiredStartUs = desiredStartUs,
        ) ?: return false

        val muxer = MediaMuxer(
            outputPath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        var muxerStarted = false

        return try {
            val videoTrack = muxer.addTrack(snapshot.videoFormat)
            val audioTrack = snapshot.audioFormat?.let { muxer.addTrack(it) }
            muxer.start()
            muxerStarted = true

            var wroteVideo = false
            var lastVideoPts = -1L
            var lastAudioPts = -1L

            forEachRecord(snapshot.files) { sample ->
                if (sample.ptsUs < startUs) return@forEachRecord true

                val trackIndex = when (sample.kind) {
                    ReplayTrackKind.Video -> {
                        wroteVideo = true
                        videoTrack
                    }
                    ReplayTrackKind.Audio -> audioTrack ?: return@forEachRecord true
                }

                val adjustedPts =
                    (sample.ptsUs - startUs).coerceAtLeast(0L)
                val monotonicPts = when (sample.kind) {
                    ReplayTrackKind.Video -> {
                        val next = maxOf(adjustedPts, lastVideoPts + 1L)
                        lastVideoPts = next
                        next
                    }
                    ReplayTrackKind.Audio -> {
                        val next = maxOf(adjustedPts, lastAudioPts + 1L)
                        lastAudioPts = next
                        next
                    }
                }

                val info = MediaCodec.BufferInfo().apply {
                    set(
                        0,
                        sample.bytes.size,
                        monotonicPts,
                        sample.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv(),
                    )
                }

                muxer.writeSampleData(
                    trackIndex,
                    ByteBuffer.wrap(sample.bytes),
                    info,
                )
                true
            }

            wroteVideo
        } catch (_: Throwable) {
            false
        } finally {
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun findReplayStartUs(
        files: List<File>,
        desiredStartUs: Long,
    ): Long? {
        var latestKeyframeBeforeOrAt: Long? = null
        var firstKeyframeAfter: Long? = null

        forEachRecordHeader(files) { kind, ptsUs, flags ->
            if (
                kind != ReplayTrackKind.Video ||
                flags and MediaCodec.BUFFER_FLAG_KEY_FRAME == 0
            ) {
                return@forEachRecordHeader true
            }

            if (ptsUs <= desiredStartUs) {
                latestKeyframeBeforeOrAt = ptsUs
                return@forEachRecordHeader true
            }

            if (firstKeyframeAfter == null) {
                firstKeyframeAfter = ptsUs
            }
            true
        }

        return latestKeyframeBeforeOrAt ?: firstKeyframeAfter
    }

    private fun forEachRecordHeader(
        files: List<File>,
        block: (ReplayTrackKind, Long, Int) -> Boolean,
    ) {
        files.forEach { file ->
            if (!file.exists() || file.length() <= 0L) return@forEach

            DataInputStream(
                BufferedInputStream(file.inputStream(), IO_BUFFER_BYTES),
            ).use { input ->
                while (true) {
                    try {
                        val kind =
                            ReplayTrackKind.fromId(input.readUnsignedByte())
                        val ptsUs = input.readLong()
                        val flags = input.readInt()
                        val size = input.readInt()

                        if (size < 0 || size > MAX_SAMPLE_BYTES) return
                        if (!block(kind, ptsUs, flags)) return

                        skipFully(input, size)
                    } catch (_: EOFException) {
                        break
                    }
                }
            }
        }
    }

    private fun forEachRecord(
        files: List<File>,
        block: (ReplayRecord) -> Boolean,
    ) {
        files.forEach { file ->
            if (!file.exists() || file.length() <= 0L) return@forEach

            DataInputStream(
                BufferedInputStream(file.inputStream(), IO_BUFFER_BYTES),
            ).use { input ->
                while (true) {
                    try {
                        val kind =
                            ReplayTrackKind.fromId(input.readUnsignedByte())
                        val ptsUs = input.readLong()
                        val flags = input.readInt()
                        val size = input.readInt()

                        if (size < 0 || size > MAX_SAMPLE_BYTES) return

                        val bytes = ByteArray(size)
                        input.readFully(bytes)

                        if (!block(ReplayRecord(kind, ptsUs, flags, bytes))) {
                            return
                        }
                    } catch (_: EOFException) {
                        break
                    }
                }
            }
        }
    }

    private fun skipFully(
        input: DataInputStream,
        byteCount: Int,
    ) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skipBytes(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                input.readByte()
                remaining -= 1
            }
        }
    }

    private fun openSegment(startPtsUs: Long): ReplaySegmentWriter {
        val writer = ReplaySegmentWriter(
            file = File(directory, "segment_${sequence++}.bin"),
            startPtsUs = startPtsUs,
        )
        current = writer
        return writer
    }

    private fun sealCurrent() {
        val writer = current ?: return
        current = null

        val segment = writer.closeAndBuild()
        if (segment.sampleCount > 0) {
            segments.addLast(segment)
        } else {
            runCatching { segment.file.delete() }
        }

        prune()
    }

    private fun prune() {
        val keepAfterUs =
            (latestPtsUs - maxDurationUs - SEGMENT_DURATION_US)
                .coerceAtLeast(0L)

        while (segments.size > 1) {
            val first = segments.first()
            if (first.endPtsUs >= keepAfterUs) break
            if (first.file.absolutePath in pinnedFiles) break

            segments.removeFirst()
            runCatching { first.file.delete() }
        }
    }

    private fun cleanupDirectoryIfDisposed() {
        if (!disposed || pinnedFiles.isNotEmpty()) return

        directory.listFiles()?.forEach { runCatching { it.delete() } }
        runCatching { directory.delete() }
    }

    private class ReplaySegmentWriter(
        val file: File,
        val startPtsUs: Long,
    ) {
        private val output = DataOutputStream(
            BufferedOutputStream(file.outputStream(), IO_BUFFER_BYTES),
        )

        var sampleCount: Int = 0
            private set

        private var endPtsUs: Long = startPtsUs

        fun write(
            kind: ReplayTrackKind,
            ptsUs: Long,
            flags: Int,
            bytes: ByteArray,
        ) {
            output.writeByte(kind.id)
            output.writeLong(ptsUs)
            output.writeInt(flags)
            output.writeInt(bytes.size)
            output.write(bytes)

            sampleCount += 1
            endPtsUs = maxOf(endPtsUs, ptsUs)
        }

        fun closeAndBuild(): ReplaySegment {
            runCatching { output.flush() }
            runCatching { output.close() }

            return ReplaySegment(
                file = file,
                startPtsUs = startPtsUs,
                endPtsUs = endPtsUs,
                sampleCount = sampleCount,
            )
        }
    }

    private data class ReplaySegment(
        val file: File,
        val startPtsUs: Long,
        val endPtsUs: Long,
        val sampleCount: Int,
    )

    private data class ReplayRecord(
        val kind: ReplayTrackKind,
        val ptsUs: Long,
        val flags: Int,
        val bytes: ByteArray,
    )

    private data class ExportSnapshot(
        val files: List<File>,
        val videoFormat: MediaFormat,
        val audioFormat: MediaFormat?,
        val latestPtsUs: Long,
    )

    companion object {
        private const val SEGMENT_DURATION_US = 5_000_000L
        private const val MAX_SAMPLE_BYTES = 16 * 1024 * 1024
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val ORPHAN_SEGMENT_GRACE_MS = 60_000L
    }
}
