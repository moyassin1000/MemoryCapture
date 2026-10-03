package com.memorycapture.app.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorycapture.app.R
import com.memorycapture.app.startup.AppStartupState
import com.memorycapture.app.startup.AppStartupViewModel
import com.memorycapture.app.recording.RecordingSessionStore
import com.memorycapture.app.recording.RecordingState
import com.memorycapture.app.recording.RecordingStateStore
import com.memorycapture.app.ui.components.GlobalRecordingPill
import com.memorycapture.app.ui.components.rememberRecordingElapsed
import com.memorycapture.app.ui.capture.CaptureScreen
import com.memorycapture.app.ui.home.HomeScreen
import com.memorycapture.app.ui.pro.ProScreen
import com.memorycapture.app.ui.player.VideoPlayerScreen
import com.memorycapture.app.ui.recordings.RecordingDetailsScreen
import com.memorycapture.app.ui.recordings.RecordingsScreen
import com.memorycapture.app.ui.settings.SettingsScreen
import com.memorycapture.app.ui.startup.SplashScreen
import com.memorycapture.app.ui.updates.UpdateCenterScreen

@Composable
fun MemoryCaptureNavHost(
    onRequestRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExitApp: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val recordingState by RecordingStateStore.state.collectAsStateWithLifecycle()
    val startedAt by RecordingSessionStore.startedAtElapsedRealtime.collectAsStateWithLifecycle()
    val pausedAt by RecordingSessionStore.pausedAtElapsedRealtime.collectAsStateWithLifecycle()
    val accumulatedPausedMs by RecordingSessionStore.accumulatedPausedMs.collectAsStateWithLifecycle()
    val elapsed = rememberRecordingElapsed(
        startedAt = startedAt,
        pausedAt = pausedAt,
        accumulatedPausedMs = accumulatedPausedMs,
    )
    val recordingPaused = recordingState is RecordingState.Paused
    val recordingActive =
        recordingState is RecordingState.Recording || recordingPaused

    val dockRoutes = setOf(
        AppDestination.Home.route,
        AppDestination.Capture.route,
        AppDestination.Recordings.route,
        AppDestination.Settings.route,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in dockRoutes) {
                PremiumDock(
                    currentRoute = currentRoute,
                    onHome = {
                        navController.navigateToDock(AppDestination.Home.route)
                    },
                    onRecordings = {
                        navController.navigateToDock(AppDestination.Recordings.route)
                    },
                    onCapture = {
                        navController.navigateToDock(AppDestination.Capture.route)
                    },
                    onSettings = {
                        navController.navigateToDock(AppDestination.Settings.route)
                    },
                )
            }
        },
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Splash.route,
            ) {
            composable(AppDestination.Splash.route) {
                val startupViewModel: AppStartupViewModel = viewModel()
                val startupState by startupViewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(startupState) {
                    if (startupState is AppStartupState.Ready) {
                        navController.navigate(AppDestination.Home.route) {
                            popUpTo(AppDestination.Splash.route) {
                                inclusive = true
                            }
                        }
                    }
                }

                SplashScreen(
                    subtitle = stringResource(R.string.splash_subtitle),
                )
            }

            composable(AppDestination.Home.route) {
                HomeScreen(
                    onStartRecording = onRequestRecording,
                    onStopRecording = onStopRecording,
                    onOpenCapture = {
                        navController.navigateToDock(AppDestination.Capture.route)
                    },
                    onOpenRecordings = {
                        navController.navigateToDock(AppDestination.Recordings.route)
                    },
                    onOpenSettings = {
                        navController.navigateToDock(AppDestination.Settings.route)
                    },
                    onOpenPro = { navController.navigate(AppDestination.Pro.route) },
                )
            }

            composable(AppDestination.Capture.route) {
                CaptureScreen(
                    onStartRecording = onRequestRecording,
                    onStopRecording = onStopRecording,
                )
            }

            composable(AppDestination.Recordings.route) {
                RecordingsScreen(
                    onOpenDetails = { uri ->
                        navController.navigate(
                            AppDestination.RecordingDetails.createRoute(uri),
                        )
                    },
                    onPlayRecording = { uri ->
                        navController.navigate(
                            AppDestination.Player.createRoute(uri),
                        )
                    },
                    onGoToCapture = {
                        navController.navigateToDock(AppDestination.Capture.route)
                    },
                )
            }

            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    onOpenUpdates = {
                        navController.navigate(AppDestination.Updates.route)
                    },
                    onOpenPro = {
                        navController.navigate(AppDestination.Pro.route)
                    },
                    onExitApp = onExitApp,
                )
            }

            composable(AppDestination.Pro.route) {
                ProScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppDestination.Updates.route) {
                UpdateCenterScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = AppDestination.RecordingDetails.route,
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val encoded = entry.arguments?.getString("uri").orEmpty()
                RecordingDetailsScreen(
                    uriString = Uri.decode(encoded),
                    onBack = { navController.popBackStack() },
                    onPlay = { uri ->
                        navController.navigate(
                            AppDestination.Player.createRoute(uri),
                        )
                    },
                )
            }

            composable(
                route = AppDestination.Player.route,
                arguments = listOf(
                    navArgument("uri") {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val encoded = entry.arguments?.getString("uri").orEmpty()
                VideoPlayerScreen(
                    uriString = Uri.decode(encoded),
                    onBack = { navController.popBackStack() },
                )
            }
            }

            if (recordingActive && currentRoute != AppDestination.Splash.route) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                ) {
                    GlobalRecordingPill(
                        elapsed = elapsed,
                        label = stringResource(
                            if (recordingPaused) R.string.status_paused
                            else R.string.status_recording,
                        ),
                        paused = recordingPaused,
                        pauseLabel = stringResource(R.string.notification_pause),
                        resumeLabel = stringResource(R.string.notification_resume),
                        stopLabel = stringResource(R.string.notification_stop),
                        onPauseResume = {
                            if (recordingPaused) onResumeRecording()
                            else onPauseRecording()
                        },
                        onStop = onStopRecording,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumDock(
    currentRoute: String?,
    onHome: () -> Unit,
    onRecordings: () -> Unit,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .shadow(20.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DockItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Home,
                label = stringResource(R.string.home_tab),
                selected = currentRoute == AppDestination.Home.route,
                onClick = onHome,
            )
            DockItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.VideoLibrary,
                label = stringResource(R.string.recordings_tab),
                selected = currentRoute == AppDestination.Recordings.route,
                onClick = onRecordings,
            )

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .shadow(18.dp, CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                                CircleShape,
                            )
                            .clickable(onClick = onCapture),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoCameraBack,
                            contentDescription = stringResource(R.string.capture_tab),
                            modifier = Modifier.size(28.dp),
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = stringResource(R.string.capture_tab),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (
                            currentRoute == AppDestination.Capture.route
                        ) {
                            FontWeight.Black
                        } else {
                            FontWeight.Medium
                        },
                        color = if (
                            currentRoute == AppDestination.Capture.route
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            DockItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Settings,
                label = stringResource(R.string.settings),
                selected = currentRoute == AppDestination.Settings.route,
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun DockItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun NavHostController.navigateToDock(route: String) {
    if (currentDestination?.route == route) return

    if (route == AppDestination.Home.route) {
        val returnedHome = popBackStack(
            route = AppDestination.Home.route,
            inclusive = false,
        )
        if (!returnedHome && currentDestination?.route != AppDestination.Home.route) {
            navigate(AppDestination.Home.route) {
                launchSingleTop = true
            }
        }
        return
    }

    navigate(route) {
        popUpTo(AppDestination.Home.route) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
