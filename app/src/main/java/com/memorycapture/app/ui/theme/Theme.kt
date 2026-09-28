package com.memorycapture.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorycapture.app.data.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A5DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E7FF),
    onPrimaryContainer = Color(0xFF101A5A),
    secondary = Color(0xFF59647B),
    tertiary = Color(0xFF7B5CF2),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFE9ECF6),
    onSurface = Color(0xFF171A23),
    onSurfaceVariant = Color(0xFF5B6070),
    outline = Color(0xFF7B8191),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BA8FF),
    onPrimary = Color(0xFF0C207E),
    primaryContainer = Color(0xFF1B2E75),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFC0C8DB),
    tertiary = Color(0xFFCDBEFF),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF080B13),
    surface = Color(0xFF101522),
    surfaceVariant = Color(0xFF1A2130),
    onSurface = Color(0xFFF1F3FF),
    onSurfaceVariant = Color(0xFFC3CADB),
    outline = Color(0xFF8E97AB),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private val AppTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
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
        typography = AppTypography,
        content = content,
    )
}
