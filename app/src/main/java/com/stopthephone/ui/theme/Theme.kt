package com.stopthephone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EEFD),
    onPrimaryContainer = Color(0xFF1F55C2),
    secondary = Color(0xFF2F6FEB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDF2),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF2F6FEB),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFEDEDF2),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE5E5EA),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFFB4232A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C8DF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A2F55),
    onPrimaryContainer = Color(0xFF9DC1FF),
    secondary = Color(0xFF4C8DF6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFF4C8DF6),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F7),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF242426),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF6369),
    onError = Color.Black,
    errorContainer = Color(0xFF3B1214),
    onErrorContainer = Color(0xFFFFB3B5),
)

/**
 * 拦截页固定用的深色配色。
 */
val BlockColors = darkColorScheme(
    primary = Color(0xFFF5A524),
    onPrimary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFF17171A),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF17171A),
    onSurfaceVariant = Color(0xFF8A8A92),
    surfaceContainerHighest = Color(0xFF17171A),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF26262B),
)

private val BaseTypography = Typography()

val StpTypography = Typography(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = BaseTypography.bodyMedium,
    bodySmall = BaseTypography.bodySmall,
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium,
    labelSmall = BaseTypography.labelSmall,
)

private val StpShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = StpTypography,
        shapes = StpShapes,
        content = content,
    )
}

/**
 * 拦截页主题，不随系统深浅色变化。
 */
@Composable
fun BlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlockColors, typography = StpTypography, shapes = StpShapes, content = content)
}
