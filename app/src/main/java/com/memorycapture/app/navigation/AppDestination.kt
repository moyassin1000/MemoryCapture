package com.memorycapture.app.navigation

sealed class AppDestination(val route: String) {
    data object Splash : AppDestination("splash")
    data object Home : AppDestination("home")
    data object Capture : AppDestination("capture")
    data object Recordings : AppDestination("recordings")
    data object Settings : AppDestination("settings")
    data object Updates : AppDestination("updates")
    data object Pro : AppDestination("pro")
    data object RecordingDetails : AppDestination("recording/{uri}") {
        fun createRoute(uri: String): String =
            "recording/${android.net.Uri.encode(uri)}"
    }
    data object Player : AppDestination("player/{uri}") {
        fun createRoute(uri: String): String =
            "player/${android.net.Uri.encode(uri)}"
    }
}
