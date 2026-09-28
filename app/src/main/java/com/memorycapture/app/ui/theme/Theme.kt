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
import com.memorycapture.app.data.preferences.ProAccent
import com.memorycapture.app.data.preferences.ThemeMode

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

private fun lightColors(accent: ProAccent) = when (accent) {
    ProAccent.Electric -> lightColorScheme(
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

    ProAccent.Aurora -> lightColorScheme(
        primary = Color(0xFF00796B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD0F5EE),
        onPrimaryContainer = Color(0xFF00382F),
        secondary = Color(0xFF4C635E),
        tertiary = Color(0xFF236489),
        error = Color(0xFFBA1A1A),
        background = Color(0xFFF3FBF8),
        surface = Color(0xFFFCFFFD),
        surfaceVariant = Color(0xFFDCE9E5),
        onSurface = Color(0xFF151D1A),
        onSurfaceVariant = Color(0xFF56615E),
        outline = Color(0xFF76817D),
    )

    ProAccent.Sunset -> lightColorScheme(
        primary = Color(0xFFB44D18),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCC),
        onPrimaryContainer = Color(0xFF3B0D00),
        secondary = Color(0xFF76584C),
        tertiary = Color(0xFF805610),
        error = Color(0xFFBA1A1A),
        background = Color(0xFFFFF8F5),
        surface = Color(0xFFFFFBFF),
        surfaceVariant = Color(0xFFF4DED5),
        onSurface = Color(0xFF201A18),
        onSurfaceVariant = Color(0xFF655C58),
        outline = Color(0xFF8B7E78),
    )
}

private fun darkColors(accent: ProAccent) = when (accent) {
    ProAccent.Electric -> darkColorScheme(
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

    ProAccent.Aurora -> darkColorScheme(
        primary = Color(0xFF67DCC7),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF005144),
        onPrimaryContainer = Color(0xFF8AF8DD),
        secondary = Color(0xFFB4CCC5),
        tertiary = Color(0xFF91CEF5),
        error = Color(0xFFFFB4AB),
        background = Color(0xFF07110F),
        surface = Color(0xFF0E1A17),
        surfaceVariant = Color(0xFF182622),
        onSurface = Color(0xFFE6F1EC),
        onSurfaceVariant = Color(0xFFBCC9C5),
        outline = Color(0xFF87938F),
    )

    ProAccent.Sunset -> darkColorScheme(
        primary = Color(0xFFFFB693),
        onPrimary = Color(0xFF5E1D00),
        primaryContainer = Color(0xFF843000),
        onPrimaryContainer = Color(0xFFFFDBCC),
        secondary = Color(0xFFE5BDAE),
        tertiary = Color(0xFFF6BF5F),
        error = Color(0xFFFFB4AB),
        background = Color(0xFF140B08),
        surface = Color(0xFF1E120E),
        surfaceVariant = Color(0xFF2C1D18),
        onSurface = Color(0xFFFFEDE7),
        onSurfaceVariant = Color(0xFFD9C2BA),
        outline = Color(0xFFA98D83),
    )
}

@Composable
fun MemoryCaptureTheme(
    themeMode: ThemeMode = ThemeMode.System,
    proAccent: ProAccent = ProAccent.Electric,
    proEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val activeAccent = if (proEnabled) proAccent else ProAccent.Electric

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors(activeAccent) else lightColors(activeAccent),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
