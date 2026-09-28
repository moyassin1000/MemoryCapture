package com.memorycapture.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.BuildConfig
import com.memorycapture.app.R
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.ThemeMode
import com.memorycapture.app.data.recordings.RecordingRepository
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenUpdates: () -> Unit,
    onExitApp: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val recordingRepository = remember { RecordingRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val currentLanguage = configuration.locales[0]?.language ?: "en"

    val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val countdownEnabled by preferences.countdownEnabled.collectAsStateWithLifecycle(initialValue = true)
    val countdownSeconds by preferences.countdownSeconds.collectAsStateWithLifecycle(initialValue = 3)
    val storageTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val storageLabel by preferences.storageLabel.collectAsStateWithLifecycle(initialValue = null)
    val updateNotifications by preferences.updateNotificationsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val recordingState by RecordingStateStore.state.collectAsStateWithLifecycle()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showActiveRecordingWarning by remember { mutableStateOf(false) }
    var recordingCount by remember { mutableStateOf(0) }
    var usedStorageBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(storageTreeUri) {
        val items = recordingRepository.loadRecordings(storageTreeUri)
        recordingCount = items.size
        usedStorageBytes = items.sumOf { it.sizeBytes }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val label = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
            scope.launch {
                preferences.setStorageTree(uri.toString(), label)
            }
        }
    }

    val folderBrowser = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.choose_language),
            options = listOf(
                "ar" to stringResource(R.string.language_arabic),
                "en" to stringResource(R.string.language_english),
            ),
            selected = currentLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelected = { tag ->
                showLanguageDialog = false
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(tag),
                )
            },
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.appearance),
            options = listOf(
                ThemeMode.System.name to stringResource(R.string.theme_system),
                ThemeMode.Light.name to stringResource(R.string.theme_light),
                ThemeMode.Dark.name to stringResource(R.string.theme_dark),
            ),
            selected = themeMode.name,
            onDismiss = { showThemeDialog = false },
            onSelected = { value ->
                showThemeDialog = false
                scope.launch {
                    preferences.setThemeMode(ThemeMode.valueOf(value))
                }
            },
        )
    }

    if (showCountdownDialog) {
        ChoiceDialog(
            title = stringResource(R.string.countdown_duration),
            options = listOf(
                "3" to stringResource(R.string.seconds_3),
                "5" to stringResource(R.string.seconds_5),
                "10" to stringResource(R.string.seconds_10),
            ),
            selected = countdownSeconds.toString(),
            onDismiss = { showCountdownDialog = false },
            onSelected = { value ->
                showCountdownDialog = false
                scope.launch { preferences.setCountdownSeconds(value.toInt()) }
            },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_app)) },
            text = { Text(stringResource(R.string.exit_app_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                    },
                ) {
                    Text(stringResource(R.string.exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showActiveRecordingWarning) {
        AlertDialog(
            onDismissRequest = { showActiveRecordingWarning = false },
            title = { Text(stringResource(R.string.recording_active)) },
            text = { Text(stringResource(R.string.stop_recording_before_exit)) },
            confirmButton = {
                TextButton(onClick = { showActiveRecordingWarning = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    val statFs = remember { StatFs(Environment.getDataDirectory().absolutePath) }
    val availableStorage = remember { formatStorage(statFs.availableBytes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.recording_section)) }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.countdown_before_recording),
                    subtitle = if (countdownEnabled) {
                        stringResource(R.string.countdown_enabled)
                    } else {
                        stringResource(R.string.countdown_disabled)
                    },
                    checked = countdownEnabled,
                    onCheckedChange = {
                        scope.launch { preferences.setCountdownEnabled(it) }
                    },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.countdown_duration),
                    subtitle = if (countdownEnabled) {
                        countdownSeconds.toString() + " " + stringResource(R.string.seconds_short)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    enabled = countdownEnabled,
                    onClick = { if (countdownEnabled) showCountdownDialog = true },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.HighQuality,
                    title = stringResource(R.string.quality),
                    subtitle = stringResource(R.string.quality_1080p),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.frame_rate),
                    subtitle = stringResource(R.string.fps_30),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.audio),
                    subtitle = stringResource(R.string.no_audio_coming_soon),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.AllInclusive,
                    title = stringResource(R.string.recording_duration),
                    subtitle = stringResource(R.string.unlimited_until_stopped),
                )
            }

            item { SectionTitle(stringResource(R.string.storage_section)) }

            item {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.storage_location),
                    subtitle = storageLabel ?: stringResource(R.string.default_storage),
                    onClick = { folderPicker.launch(storageTreeUri?.let(Uri::parse)) },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.open_recordings_folder),
                    subtitle = storageLabel ?: stringResource(R.string.default_storage),
                    onClick = {
                        folderBrowser.launch(storageTreeUri?.let(Uri::parse))
                    },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.VideoLibrary,
                    title = stringResource(R.string.recording_count),
                    subtitle = recordingCount.toString(),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.used_storage),
                    subtitle = formatStorage(usedStorageBytes),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.available_storage),
                    subtitle = availableStorage,
                )
            }

            if (storageLabel != null) {
                item {
                    SettingsRow(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.restore_default_storage),
                        subtitle = stringResource(R.string.default_storage),
                        onClick = {
                            scope.launch { preferences.setStorageTree(null, null) }
                        },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.appearance_section)) }

            item {
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language_title),
                    subtitle = if (currentLanguage == "ar") {
                        stringResource(R.string.language_arabic)
                    } else {
                        stringResource(R.string.language_english)
                    },
                    onClick = { showLanguageDialog = true },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.appearance),
                    subtitle = when (themeMode) {
                        ThemeMode.System -> stringResource(R.string.theme_system)
                        ThemeMode.Light -> stringResource(R.string.theme_light)
                        ThemeMode.Dark -> stringResource(R.string.theme_dark)
                    },
                    onClick = { showThemeDialog = true },
                )
            }

            item { SectionTitle(stringResource(R.string.app_section)) }

            item {
                SettingsRow(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.update_center),
                    subtitle = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                    onClick = onOpenUpdates,
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.update_notifications),
                    subtitle = stringResource(R.string.update_notifications_body),
                    checked = updateNotifications,
                    onCheckedChange = {
                        scope.launch { preferences.setUpdateNotificationsEnabled(it) }
                    },
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.PrivacyTip,
                    title = stringResource(R.string.settings_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_body),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_app),
                    subtitle = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.ExitToApp,
                    title = stringResource(R.string.exit_app),
                    subtitle = stringResource(R.string.exit_app_subtitle),
                    onClick = {
                        if (recordingState is RecordingState.Recording ||
                            recordingState is RecordingState.Paused
                        ) {
                            showActiveRecordingWarning = true
                        } else {
                            showExitDialog = true
                        }
                    },
                    destructive = true,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.local_recordings_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 30.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option.first) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == option.first,
                            onClick = { onSelected(option.first) },
                        )
                        Text(
                            text = option.second,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (index < options.lastIndex) HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun formatStorage(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes / 1024.0 / 1024.0
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}
