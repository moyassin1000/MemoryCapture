package com.memorycapture.app.data.recordings

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordingItem(
    val uri: String,
    val displayName: String,
    val dateAddedMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)

class RecordingRepository(
    private val context: Context,
) {
    suspend fun loadRecordings(customTreeUri: String?): List<RecordingItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<RecordingItem>()
            items += loadMediaStoreRecordings()
            if (!customTreeUri.isNullOrBlank()) {
                items += loadTreeRecordings(customTreeUri)
            }
            items
                .distinctBy { it.uri }
                .sortedByDescending { it.dateAddedMillis }
        }

    suspend fun delete(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrElse {
            DocumentFile.fromSingleUri(context, uri)?.delete() == true
        }
    }

    suspend fun rename(uriString: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val safeName = if (newName.endsWith(".mp4", ignoreCase = true)) newName else "$newName.mp4"

        val updated = runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                },
                null,
                null,
            )
        }.getOrDefault(0)

        if (updated > 0) {
            true
        } else {
            DocumentFile.fromSingleUri(context, uri)?.renameTo(safeName) == true
        }
    }

    private fun loadMediaStoreRecordings(): List<RecordingItem> {
        val result = mutableListOf<RecordingItem>()
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%Movies/MemoryCapture%")
        } else {
            selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf("MemoryCapture_%")
        }

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                result += RecordingItem(
                    uri = uri.toString(),
                    displayName = cursor.getString(nameIndex) ?: "MemoryCapture.mp4",
                    dateAddedMillis = cursor.getLong(dateIndex) * 1000L,
                    durationMillis = cursor.getLong(durationIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    width = cursor.getInt(widthIndex),
                    height = cursor.getInt(heightIndex),
                )
            }
        }
        return result
    }

    private fun loadTreeRecordings(treeUriString: String): List<RecordingItem> {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && (it.type == "video/mp4" || it.name?.endsWith(".mp4", true) == true) }
            .map { file ->
                val metadata = readMetadata(file.uri)
                RecordingItem(
                    uri = file.uri.toString(),
                    displayName = file.name ?: "MemoryCapture.mp4",
                    dateAddedMillis = file.lastModified(),
                    durationMillis = metadata.duration,
                    sizeBytes = file.length(),
                    width = metadata.width,
                    height = metadata.height,
                )
            }
    }

    private fun readMetadata(uri: Uri): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                )?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                )?.toIntOrNull() ?: 0,
            )
        } catch (_: Throwable) {
            VideoMetadata()
        } finally {
            retriever.release()
        }
    }

    private data class VideoMetadata(
        val duration: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )
}
