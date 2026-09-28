package com.memorycapture.app.data.update

import com.memorycapture.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
)

class UpdateRepository {
    suspend fun checkLatest(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MemoryCapture/${BuildConfig.VERSION_NAME}")
            }

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext UpdateState.UpToDate
                }
                if (connection.responseCode !in 200..299) {
                    return@withContext UpdateState.Error("HTTP ${connection.responseCode}")
                }

                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name").ifBlank { return@withContext UpdateState.UpToDate }
                val notes = json.optString("body")
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }

                val latest = normalize(tag)
                val current = normalize(BuildConfig.VERSION_NAME)
                if (latest == current) {
                    UpdateState.UpToDate
                } else {
                    UpdateState.Available(
                        UpdateInfo(
                            version = tag,
                            notes = notes,
                            apkUrl = apkUrl,
                        ),
                    )
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            UpdateState.Error(error.message ?: "Unable to check for updates")
        }
    }

    private fun normalize(version: String): String =
        version.trim().removePrefix("v").substringBefore("-")

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/moyassin1000/MemoryCapture/releases/latest"
    }
}
