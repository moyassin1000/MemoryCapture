package com.memorycapture.app.ui.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memorycapture.app.BuildConfig
import com.memorycapture.app.R
import com.memorycapture.app.data.update.UpdateRepository
import com.memorycapture.app.data.update.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCenterScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { UpdateRepository() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_center)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.manual_updates_only),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val current = state) {
                UpdateState.Idle -> {
                    Text(stringResource(R.string.update_not_checked))
                }

                UpdateState.Checking -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.checking_for_updates),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                UpdateState.UpToDate -> {
                    StatusCard(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.up_to_date),
                        body = stringResource(R.string.up_to_date_body),
                    )
                }

                is UpdateState.Error -> {
                    StatusCard(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.update_check_failed),
                        body = current.message,
                    )
                }

                is UpdateState.Available -> {
                    StatusCard(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.update_available),
                        body = stringResource(R.string.available_version, current.info.version),
                    )
                    if (current.info.notes.isNotBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.release_notes),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    current.info.notes,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    current.info.apkUrl?.let { url ->
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(
                                stringResource(R.string.download_update),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            if (state !is UpdateState.Checking) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        state = UpdateState.Checking
                        scope.launch {
                            state = repository.checkLatest()
                        }
                    },
                ) {
                    Text(stringResource(R.string.check_for_updates))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
