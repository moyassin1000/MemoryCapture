package com.memorycapture.app.data.recordings

import android.content.Context

class RecordingHighlightRepository(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(
        recordingUri: String?,
        displayName: String,
        highlightsMillis: List<Long>,
    ) {
        if (highlightsMillis.isEmpty()) return
        val value = highlightsMillis
            .filter { it >= 0L }
            .distinct()
            .sorted()
            .joinToString(",")

        preferences.edit()
            .putString(key(recordingUri, displayName), value)
            .apply()
    }

    fun loadByUri(recordingUri: String): List<Long> =
        parse(preferences.getString("highlights:$recordingUri", null))

    fun load(
        recordingUri: String?,
        displayName: String,
    ): List<Long> {
        val direct = preferences.getString(
            key(recordingUri, displayName),
            null,
        )

        val fallback = if (direct == null && !recordingUri.isNullOrBlank()) {
            preferences.getString(key(null, displayName), null)
        } else {
            null
        }

        return parse(direct ?: fallback)
    }

    private fun parse(value: String?): List<Long> =
        value
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.filter { it >= 0L }
            ?.distinct()
            ?.sorted()
            ?: emptyList()

    fun remove(
        recordingUri: String?,
        displayName: String,
    ) {
        preferences.edit()
            .remove(key(recordingUri, displayName))
            .remove(key(null, displayName))
            .apply()
    }

    private fun key(
        recordingUri: String?,
        displayName: String,
    ): String {
        val identity = recordingUri
            ?.takeIf { it.isNotBlank() }
            ?: "name:$displayName"
        return "highlights:$identity"
    }

    companion object {
        private const val PREFS_NAME = "memorycapture_recording_highlights"
    }
}
