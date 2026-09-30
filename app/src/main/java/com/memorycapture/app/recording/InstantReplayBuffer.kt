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
    private var current: ReplaySegmentWriter? = null
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var latestPtsUs = 0L
    private var sequence = 0

    @Synchronized
    fun registerFormat(
        kind: ReplayTrackKind,
        format: MediaFormat,
    ) {
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
        if (info.size <= 0) return

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

    @Synchronized
    fun export(
        outputPath: String,
        durationUs: Long,
    ): Boolean {
        sealCurrent()
        if (segments.isEmpty()) return false

        val video = videoFormat ?: return false
        val audio = audioFormat
        val files = segments.map { it.file }
        val desiredStartUs = (latestPtsUs - durationUs).coerceAtLeast(0L)

        val records = files.flatMap { readRecords(it) }
        val firstVideoKeyframe = records.firstOrNull {
            it.kind == ReplayTrackKind.Video &&
                it.ptsUs >= desiredStartUs &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        } ?: records.firstOrNull {
            it.kind == ReplayTrackKind.Video &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        } ?: return false

        val startUs = firstVideoKeyframe.ptsUs
        val selected = records.filter { it.ptsUs >= startUs }
        if (selected.none { it.kind == ReplayTrackKind.Video }) return false

        val muxer = MediaMuxer(
            outputPath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        var started = false
        return try {
            val videoTrack = muxer.addTrack(video)
            val audioTrack = audio?.let { muxer.addTrack(it) }
            muxer.start()
            started = true

            var lastVideoPts = -1L
            var lastAudioPts = -1L

            selected.forEach { sample ->
                val adjustedPts = (sample.ptsUs - startUs).coerceAtLeast(0L)
                val track = when (sample.kind) {
                    ReplayTrackKind.Video -> videoTrack
                    ReplayTrackKind.Audio -> audioTrack ?: return@forEach
                }

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
                    track,
                    ByteBuffer.wrap(sample.bytes),
                    info,
                )
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            openSegment(latestPtsUs)
        }
    }

    @Synchronized
    fun clear() {
        runCatching { current?.closeAndBuild() }
        current = null
        segments.forEach { runCatching { it.file.delete() } }
        segments.clear()
        directory.listFiles()?.forEach { runCatching { it.delete() } }
        videoFormat = null
        audioFormat = null
        latestPtsUs = 0L
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
        val keepAfterUs = (latestPtsUs - maxDurationUs - SEGMENT_DURATION_US)
            .coerceAtLeast(0L)
        while (segments.size > 1) {
            val first = segments.first()
            if (first.endPtsUs >= keepAfterUs) break
            segments.removeFirst()
            runCatching { first.file.delete() }
        }
    }

    private fun readRecords(file: File): List<ReplayRecord> {
        if (!file.exists() || file.length() <= 0L) return emptyList()
        val result = mutableListOf<ReplayRecord>()
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            while (true) {
                try {
                    val kind = ReplayTrackKind.fromId(input.readUnsignedByte())
                    val ptsUs = input.readLong()
                    val flags = input.readInt()
                    val size = input.readInt()
                    if (size < 0 || size > MAX_SAMPLE_BYTES) break
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    result += ReplayRecord(kind, ptsUs, flags, bytes)
                } catch (_: EOFException) {
                    break
                }
            }
        }
        return result
    }

    private class ReplaySegmentWriter(
        val file: File,
        val startPtsUs: Long,
    ) {
        private val output = DataOutputStream(
            BufferedOutputStream(file.outputStream()),
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

    companion object {
        private const val SEGMENT_DURATION_US = 5_000_000L
        private const val MAX_SAMPLE_BYTES = 16 * 1024 * 1024
    }
}
