package com.memorycapture.app.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Settings : AppDestination("settings")
}
