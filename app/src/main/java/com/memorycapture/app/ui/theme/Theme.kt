package com.memorycapture.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.memorycapture.app.data.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B5FFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E9FF),
    onPrimaryContainer = Color(0xFF10184F),
    secondary = Color(0xFF586174),
    tertiary = Color(0xFF7A5CFA),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF4F6FC),
    surface = Color(0xFFFDFDFF),
    surfaceVariant = Color(0xFFE9ECF5),
    onSurface = Color(0xFF171921),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFF7A8193),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95A4FF),
    onPrimary = Color(0xFF071A78),
    primaryContainer = Color(0xFF1A2B72),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFBCC5D9),
    tertiary = Color(0xFFC8B8FF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF080B14),
    surface = Color(0xFF101522),
    surfaceVariant = Color(0xFF1A2130),
    onSurface = Color(0xFFF1F3FF),
    onSurfaceVariant = Color(0xFFC3CADB),
    outline = Color(0xFF8D96AA),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

@Composable
fun MemoryCaptureTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
