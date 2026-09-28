package com.memorycapture.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorycapture.app.R
import com.memorycapture.app.startup.AppStartupState
import com.memorycapture.app.startup.AppStartupViewModel
import com.memorycapture.app.ui.capture.CaptureScreen
import com.memorycapture.app.ui.home.HomeScreen
import com.memorycapture.app.ui.pro.ProScreen
import com.memorycapture.app.ui.recordings.RecordingDetailsScreen
import com.memorycapture.app.ui.recordings.RecordingsScreen
import com.memorycapture.app.ui.settings.SettingsScreen
import com.memorycapture.app.ui.startup.SplashScreen
import com.memorycapture.app.ui.updates.UpdateCenterScreen

private data class BottomDestination(
    val destination: AppDestination,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun MemoryCaptureNavHost(
    onRequestRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExitApp: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomDestinations = listOf(
        BottomDestination(AppDestination.Home, R.string.home_tab, Icons.Default.Home),
        BottomDestination(AppDestination.Capture, R.string.capture_tab, Icons.Default.VideoCameraBack),
        BottomDestination(AppDestination.Recordings, R.string.recordings_tab, Icons.Default.VideoLibrary),
        BottomDestination(AppDestination.Settings, R.string.settings, Icons.Default.Settings),
    )

    val showBottomBar = currentRoute in bottomDestinations.map { it.destination.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    tonalElevation = 10.dp,
                ) {
                    bottomDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.destination.route,
                            onClick = {
                                navController.navigate(item.destination.route) {
                                    popUpTo(AppDestination.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Splash.route,
            modifier = Modifier.padding(outerPadding),
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
                    onOpenCapture = { navController.navigate(AppDestination.Capture.route) },
                    onOpenRecordings = { navController.navigate(AppDestination.Recordings.route) },
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
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
                    onGoToCapture = {
                        navController.navigate(AppDestination.Capture.route)
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
                )
            }
        }
    }
}
