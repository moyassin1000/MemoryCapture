package com.memorycapture.app.recording

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import java.io.File

class RecordingStorageSpaceResolver(
    private val context: Context,
) {
    fun recordingDestinationAvailableBytes(customTreeUri: String?): Long? {
        if (customTreeUri.isNullOrBlank()) {
            return statAvailableBytes(Environment.getExternalStorageDirectory())
        }

        val uri = runCatching { Uri.parse(customTreeUri) }.getOrNull()
            ?: return null

        if (uri.authority != EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY) {
            return null
        }

        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull() ?: return null

        val volumeId = documentId.substringBefore(':').takeIf { it.isNotBlank() }
            ?: return null

        if (volumeId.equals(PRIMARY_VOLUME_ID, ignoreCase = true)) {
            return statAvailableBytes(Environment.getExternalStorageDirectory())
        }

        return resolveExternalVolumeDirectory(volumeId)
            ?.let(::statAvailableBytes)
    }

    fun workingStorageAvailableBytes(): Long {
        val cacheBytes = statAvailableBytes(context.cacheDir)
        val filesBytes = statAvailableBytes(context.filesDir)

        return listOfNotNull(cacheBytes, filesBytes)
            .minOrNull()
            ?: 0L
    }

    private fun resolveExternalVolumeDirectory(volumeId: String): File? {
        val marker = "/storage/$volumeId/"
        return context.getExternalFilesDirs(null)
            .filterNotNull()
            .firstOrNull { directory ->
                val normalized = directory.absolutePath.replace('\\', '/')
                normalized.contains(marker, ignoreCase = true)
            }
    }

    private fun statAvailableBytes(file: File): Long? =
        runCatching {
            StatFs(file.absolutePath).availableBytes
        }.getOrNull()

    companion object {
        private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY =
            "com.android.externalstorage.documents"
        private const val PRIMARY_VOLUME_ID = "primary"
    }
}
