package com.memorycapture.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B1F3A),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF5E35B1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C7E8),
    secondary = Color(0xFFB39DDB),
    tertiary = Color(0xFFD1C4E9),
)

@Composable
fun MemoryCaptureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
