package com.memorycapture.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memorycapture.app.R
import com.memorycapture.app.billing.ProBillingManager
import com.memorycapture.app.data.preferences.AppPreferences
import com.memorycapture.app.data.preferences.ProAccent
import com.memorycapture.app.data.recordings.RecordingRepository
import com.memorycapture.app.ui.components.PremiumBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val preferences = remember { AppPreferences(context.applicationContext) }
    val repository = remember { RecordingRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val billingState by ProBillingManager.state.collectAsStateWithLifecycle()
    val accent by preferences.proAccent.collectAsStateWithLifecycle(
        initialValue = ProAccent.Electric,
    )
    val storageTreeUri by preferences.storageTreeUri.collectAsStateWithLifecycle(
        initialValue = null,
    )

    var recordingCount by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var totalSizeBytes by remember { mutableLongStateOf(0L) }
    var maxResolution by remember { mutableStateOf("—") }

    LaunchedEffect(storageTreeUri, billingState.isPro) {
        if (billingState.isPro) {
            val recordings = repository.loadRecordings(storageTreeUri)
            recordingCount = recordings.size
            totalDurationMs = recordings.sumOf { item -> item.durationMillis }
            totalSizeBytes = recordings.sumOf { item -> item.sizeBytes }
            val maxItem = recordings.maxByOrNull { item -> item.width.toLong() * item.height.toLong() }
            maxResolution = if (maxItem != null) {
                maxItem.width.toString() + "×" + maxItem.height.toString()
            } else {
                "—"
            }
        }
    }

    PremiumBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    title = {
                        Text(
                            stringResource(R.string.pro_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    navigationIcon = {
                        Text(
                            text = "‹",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .clickable(onClick = onBack)
                                .padding(horizontal = 18.dp),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(18.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f),
                                        ),
                                    ),
                                )
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (billingState.isPro) {
                                    stringResource(R.string.pro_active)
                                } else {
                                    stringResource(R.string.unlock_pro)
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                text = if (billingState.isPro) {
                                    stringResource(R.string.pro_active_body)
                                } else {
                                    stringResource(R.string.pro_intro)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (!billingState.isPro) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !billingState.loading && activity != null,
                                    onClick = {
                                        activity?.let(ProBillingManager::launchPurchase)
                                    },
                                ) {
                                    if (billingState.loading) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.padding(3.dp),
                                        )
                                    } else {
                                        Text(
                                            stringResource(
                                                R.string.get_pro_price,
                                                billingState.priceLabel
                                                    ?: stringResource(R.string.google_play_price),
                                            ),
                                        )
                                    }
                                }

                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = ProBillingManager::restorePurchases,
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Text(
                                        stringResource(R.string.restore_purchases),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        stringResource(R.string.pro_lifetime_owned),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            billingState.message?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surface,
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                        ),
                                    ),
                                )
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = stringResource(R.string.pro_showcase_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Text(
                                text = stringResource(R.string.pro_showcase_body),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.pro_compare_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, MaterialTheme.shapes.large),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProComparisonRow(
                                title = stringResource(R.string.pro_compare_core),
                                freeIncluded = true,
                                proIncluded = true,
                            )
                            ProComparisonRow(
                                title = stringResource(R.string.pro_compare_themes),
                                freeIncluded = false,
                                proIncluded = true,
                            )
                            ProComparisonRow(
                                title = stringResource(R.string.pro_compare_insights),
                                freeIncluded = false,
                                proIncluded = true,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.pro_features),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }

                item {
                    ProFeatureCard(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.pro_feature_themes),
                        body = stringResource(R.string.pro_feature_themes_body),
                    )
                }

                item {
                    ProFeatureCard(
                        icon = Icons.Default.Analytics,
                        title = stringResource(R.string.pro_feature_insights),
                        body = stringResource(R.string.pro_feature_insights_body),
                    )
                }

                item {
                    ProFeatureCard(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.pro_feature_experience),
                        body = stringResource(R.string.pro_feature_experience_body),
                    )
                }

                item {
                    ProFeatureCard(
                        icon = Icons.Default.CloudDone,
                        title = stringResource(R.string.pro_feature_lifetime),
                        body = stringResource(R.string.pro_feature_lifetime_body),
                    )
                }

                if (billingState.isPro) {
                    item {
                        Text(
                            stringResource(R.string.pro_themes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ProThemeChip(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.pro_theme_electric),
                                selected = accent == ProAccent.Electric,
                                colors = listOf(Color(0xFF4A5DFF), Color(0xFF7B5CF2)),
                                onClick = {
                                    scope.launch {
                                        preferences.setProAccent(ProAccent.Electric)
                                    }
                                },
                            )
                            ProThemeChip(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.pro_theme_aurora),
                                selected = accent == ProAccent.Aurora,
                                colors = listOf(Color(0xFF00796B), Color(0xFF236489)),
                                onClick = {
                                    scope.launch {
                                        preferences.setProAccent(ProAccent.Aurora)
                                    }
                                },
                            )
                            ProThemeChip(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.pro_theme_sunset),
                                selected = accent == ProAccent.Sunset,
                                colors = listOf(Color(0xFFB44D18), Color(0xFF805610)),
                                onClick = {
                                    scope.launch {
                                        preferences.setProAccent(ProAccent.Sunset)
                                    }
                                },
                            )
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.pro_insights),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            InsightRow(
                                stringResource(R.string.recording_count),
                                recordingCount.toString(),
                            )
                            InsightRow(
                                stringResource(R.string.pro_total_duration),
                                formatDuration(totalDurationMs),
                            )
                            InsightRow(
                                stringResource(R.string.used_storage),
                                formatBytes(totalSizeBytes),
                            )
                            InsightRow(
                                stringResource(R.string.pro_highest_resolution),
                                maxResolution,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.pro_purchase_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProComparisonRow(
    title: String,
    freeIncluded: Boolean,
    proIncluded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = if (freeIncluded) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = stringResource(R.string.pro_compare_free),
                tint = if (freeIncluded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                imageVector = if (proIncluded) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = stringResource(R.string.pro_compare_pro),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ProFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProThemeChip(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .shadow(if (selected) 12.dp else 4.dp, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(colors),
                        MaterialTheme.shapes.small,
                    )
                    .padding(vertical = 16.dp),
            ) {}
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InsightRow(
    label: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Black)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    return if (hours > 0) {
        hours.toString() + "h " + minutes.toString() + "m"
    } else {
        minutes.toString() + "m"
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
