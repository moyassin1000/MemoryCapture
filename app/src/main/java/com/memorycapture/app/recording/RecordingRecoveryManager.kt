package com.memorycapture.app.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecoveredRecording(
    val displayName: String,
    val uri: Uri?,
    val locationLabel: String,
)

class RecordingRecoveryManager(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private val recoveryDirectory =
        File(context.filesDir, "recording_recovery").apply { mkdirs() }

    val checkpointFile: File
        get() = File(recoveryDirectory, CHECKPOINT_NAME)

    val checkpointTempFile: File
        get() = File(recoveryDirectory, CHECKPOINT_TEMP_NAME)

    private val checkpointBackupFile: File
        get() = File(recoveryDirectory, CHECKPOINT_BACKUP_NAME)

    fun markSessionActive() {
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_SESSION_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markSessionClosed() {
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .remove(KEY_SESSION_STARTED_AT)
            .apply()
        runCatching { checkpointFile.delete() }
        runCatching { checkpointTempFile.delete() }
        runCatching { checkpointBackupFile.delete() }
    }

    fun commitCheckpoint(): Boolean {
        val temp = checkpointTempFile
        if (!temp.exists() || temp.length() <= MIN_RECOVERY_BYTES) {
            runCatching { temp.delete() }
            return false
        }

        val current = checkpointFile
        val backup = checkpointBackupFile
        runCatching { backup.delete() }

        if (current.exists()) {
            if (!current.renameTo(backup)) {
                runCatching { temp.delete() }
                return false
            }
        }

        val committed = temp.renameTo(current)
        if (!committed) {
            if (backup.exists()) {
                runCatching { backup.renameTo(current) }
            }
            runCatching { temp.delete() }
            return false
        }

        runCatching { backup.delete() }
        return true
    }

    fun hasInterruptedSession(): Boolean =
        preferences.getBoolean(KEY_SESSION_ACTIVE, false)

    fun clearInterruptedFlag() {
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .remove(KEY_SESSION_STARTED_AT)
            .apply()
    }

    fun recoverIfNeeded(): RecoveredRecording? {
        if (!hasInterruptedSession()) return null

        val file = when {
            checkpointFile.exists() && checkpointFile.length() > MIN_RECOVERY_BYTES ->
                checkpointFile
            checkpointBackupFile.exists() &&
                checkpointBackupFile.length() > MIN_RECOVERY_BYTES ->
                checkpointBackupFile
            else -> null
        } ?: run {
            runCatching { checkpointFile.delete() }
            runCatching { checkpointTempFile.delete() }
            runCatching { checkpointBackupFile.delete() }
            return null
        }

        val recovered = importRecoveredFile(file)
        if (recovered != null) {
            clearInterruptedFlag()
            runCatching { checkpointFile.delete() }
            runCatching { checkpointTempFile.delete() }
            runCatching { checkpointBackupFile.delete() }
        }
        return recovered
    }

    private fun importRecoveredFile(file: File): RecoveredRecording? {
        val displayName =
            "Recovered_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                ".mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/MemoryCapture/Recovered",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return null

            val copied = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)

            if (!copied) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }

            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
            runCatching { file.delete() }

            return RecoveredRecording(
                displayName = displayName,
                uri = uri,
                locationLabel = Environment.DIRECTORY_MOVIES + "/MemoryCapture/Recovered",
            )
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val directory = File(root, "MemoryCapture/Recovered").apply { mkdirs() }
        val target = File(directory, displayName)

        return runCatching {
            file.copyTo(target, overwrite = true)
            file.delete()
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf("video/mp4"),
                null,
            )
            RecoveredRecording(
                displayName = target.name,
                uri = null,
                locationLabel = target.parentFile?.absolutePath ?: target.absolutePath,
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "memorycapture_recovery"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_STARTED_AT = "session_started_at"
        private const val CHECKPOINT_NAME = "last_recovery_checkpoint.mp4"
        private const val CHECKPOINT_TEMP_NAME = "last_recovery_checkpoint.tmp.mp4"
        private const val CHECKPOINT_BACKUP_NAME = "last_recovery_checkpoint.bak.mp4"
        private const val MIN_RECOVERY_BYTES = 16 * 1024L
    }
}
