package com.memorycapture.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.memorycapture.app.ui.home.HomeScreen
import com.memorycapture.app.ui.settings.SettingsScreen

@Composable
fun MemoryCaptureNavHost(
    onRequestRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppDestination.Home.route) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                onStartRecording = onRequestRecording,
                onStopRecording = onStopRecording,
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
