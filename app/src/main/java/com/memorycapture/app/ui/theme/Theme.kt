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
    primary = Color(0xFF3656D4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF10215F),
    secondary = Color(0xFF59647A),
    tertiary = Color(0xFF7851A9),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurface = Color(0xFF191B22),
    onSurfaceVariant = Color(0xFF5E616B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF05258B),
    primaryContainer = Color(0xFF1D3EA9),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC3C7D5),
    tertiary = Color(0xFFE0B8FF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111318),
    surface = Color(0xFF181A20),
    surfaceVariant = Color(0xFF252831),
    onSurface = Color(0xFFE4E2E9),
    onSurfaceVariant = Color(0xFFC7C6D0),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
