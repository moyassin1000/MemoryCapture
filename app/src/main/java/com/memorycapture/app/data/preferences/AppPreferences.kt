package com.memorycapture.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.memoryCaptureDataStore by preferencesDataStore(
    name = "memorycapture_settings",
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class ProAccent {
    Electric,
    Aurora,
    Sunset,
}

enum class AudioMode {
    None,
    Microphone,
    DeviceAudio,
    DeviceAndMic,
}

class AppPreferences(
    private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.memoryCaptureDataStore.data.map { preferences ->
        runCatching {
            ThemeMode.valueOf(preferences[KEY_THEME] ?: ThemeMode.System.name)
        }.getOrDefault(ThemeMode.System)
    }

    val countdownEnabled: Flow<Boolean> = context.memoryCaptureDataStore.data.map {
        it[KEY_COUNTDOWN_ENABLED] ?: true
    }

    val countdownSeconds: Flow<Int> = context.memoryCaptureDataStore.data.map {
        (it[KEY_COUNTDOWN_SECONDS] ?: 3).coerceIn(3, 10)
    }

    val storageTreeUri: Flow<String?> = context.memoryCaptureDataStore.data.map {
        it[KEY_STORAGE_TREE_URI]
    }

    val storageLabel: Flow<String?> = context.memoryCaptureDataStore.data.map {
        it[KEY_STORAGE_LABEL]
    }

    val updateNotificationsEnabled: Flow<Boolean> = context.memoryCaptureDataStore.data.map {
        it[KEY_UPDATE_NOTIFICATIONS] ?: false
    }

    val favoriteRecordings: Flow<Set<String>> = context.memoryCaptureDataStore.data.map {
        it[KEY_FAVORITE_RECORDINGS] ?: emptySet()
    }

    val proEntitlementCached: Flow<Boolean> = context.memoryCaptureDataStore.data.map {
        it[KEY_PRO_ENTITLEMENT] ?: false
    }

    val proAccent: Flow<ProAccent> = context.memoryCaptureDataStore.data.map { preferences ->
        runCatching {
            ProAccent.valueOf(preferences[KEY_PRO_ACCENT] ?: ProAccent.Electric.name)
        }.getOrDefault(ProAccent.Electric)
    }

    val audioMode: Flow<AudioMode> = context.memoryCaptureDataStore.data.map { preferences ->
        runCatching {
            AudioMode.valueOf(preferences[KEY_AUDIO_MODE] ?: AudioMode.DeviceAndMic.name)
        }.getOrDefault(AudioMode.DeviceAndMic)
    }

    val microphoneDeviceId: Flow<Int> = context.memoryCaptureDataStore.data.map {
        it[KEY_MICROPHONE_DEVICE_ID] ?: -1
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.memoryCaptureDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setCountdownEnabled(enabled: Boolean) {
        context.memoryCaptureDataStore.edit { it[KEY_COUNTDOWN_ENABLED] = enabled }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        context.memoryCaptureDataStore.edit {
            it[KEY_COUNTDOWN_SECONDS] = seconds.coerceIn(3, 10)
        }
    }

    suspend fun setStorageTree(uri: String?, label: String?) {
        context.memoryCaptureDataStore.edit {
            if (uri == null) {
                it.remove(KEY_STORAGE_TREE_URI)
                it.remove(KEY_STORAGE_LABEL)
            } else {
                it[KEY_STORAGE_TREE_URI] = uri
                it[KEY_STORAGE_LABEL] = label.orEmpty()
            }
        }
    }

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) {
        context.memoryCaptureDataStore.edit {
            it[KEY_UPDATE_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setProEntitlementCached(enabled: Boolean) {
        context.memoryCaptureDataStore.edit { it[KEY_PRO_ENTITLEMENT] = enabled }
    }

    suspend fun setProAccent(accent: ProAccent) {
        context.memoryCaptureDataStore.edit { it[KEY_PRO_ACCENT] = accent.name }
    }

    suspend fun setAudioMode(mode: AudioMode) {
        context.memoryCaptureDataStore.edit { it[KEY_AUDIO_MODE] = mode.name }
    }

    suspend fun setMicrophoneDeviceId(deviceId: Int) {
        context.memoryCaptureDataStore.edit { it[KEY_MICROPHONE_DEVICE_ID] = deviceId }
    }

    suspend fun toggleFavoriteRecording(uri: String) {
        context.memoryCaptureDataStore.edit { preferences ->
            val current = preferences[KEY_FAVORITE_RECORDINGS] ?: emptySet()
            preferences[KEY_FAVORITE_RECORDINGS] =
                if (uri in current) current - uri else current + uri
        }
    }

    suspend fun removeFavoriteRecording(uri: String) {
        context.memoryCaptureDataStore.edit { preferences ->
            val current = preferences[KEY_FAVORITE_RECORDINGS] ?: emptySet()
            if (uri in current) {
                preferences[KEY_FAVORITE_RECORDINGS] = current - uri
            }
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_COUNTDOWN_ENABLED = booleanPreferencesKey("countdown_enabled")
        private val KEY_COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        private val KEY_STORAGE_TREE_URI = stringPreferencesKey("storage_tree_uri")
        private val KEY_STORAGE_LABEL = stringPreferencesKey("storage_label")
        private val KEY_UPDATE_NOTIFICATIONS = booleanPreferencesKey("update_notifications")
        private val KEY_FAVORITE_RECORDINGS = stringSetPreferencesKey("favorite_recordings")
        private val KEY_PRO_ENTITLEMENT = booleanPreferencesKey("pro_entitlement_cached")
        private val KEY_PRO_ACCENT = stringPreferencesKey("pro_accent")
        private val KEY_AUDIO_MODE = stringPreferencesKey("audio_mode")
        private val KEY_MICROPHONE_DEVICE_ID = intPreferencesKey("microphone_device_id")
    }
}
