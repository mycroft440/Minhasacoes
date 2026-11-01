package com.minhasacoes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class MarketColors(
    val up: Color,
    val down: Color,
    val neutral: Color,
    val cardBorder: Color,
)

private val DarkMarket = MarketColors(
    up = Color(0xFF2BD38A),
    down = Color(0xFFFF5A67),
    neutral = Color(0xFF8A94A6),
    cardBorder = Color(0xFF1F2632),
)

private val LightMarket = MarketColors(
    up = Color(0xFF0E9F62),
    down = Color(0xFFE0364A),
    neutral = Color(0xFF6B7280),
    cardBorder = Color(0xFFE7EAEF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF1E2A4D),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF8AA4FF),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE9EDF3),
    surface = Color(0xFF131820),
    onSurface = Color(0xFFE9EDF3),
    surfaceVariant = Color(0xFF1B212C),
    onSurfaceVariant = Color(0xFF8A94A6),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF131820),
    surfaceContainer = Color(0xFF161C25),
    surfaceContainerHigh = Color(0xFF1A202A),
    surfaceContainerHighest = Color(0xFF222935),
    outline = Color(0xFF2E3644),
    outlineVariant = Color(0xFF1F2632),
    error = Color(0xFFFF5A67),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F63F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9FF),
    onPrimaryContainer = Color(0xFF14204A),
    secondary = Color(0xFF3F63F2),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF0F1419),
    surface = Color.White,
    onSurface = Color(0xFF0F1419),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEDEFF3),
    outline = Color(0xFFD5D9E0),
    outlineVariant = Color(0xFFE7EAEF),
    error = Color(0xFFE0364A),
)

private const val TNUM = "tnum"

private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(
            fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, fontFeatureSettings = TNUM,
        ),
        headlineLarge = t.headlineLarge.copy(
            fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM,
        ),
        headlineMedium = t.headlineMedium.copy(
            fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
        ),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = TNUM),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = TNUM),
        bodySmall = t.bodySmall.copy(fontFeatureSettings = TNUM),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
        labelMedium = t.labelMedium.copy(fontFeatureSettings = TNUM),
    )
}

val LocalMarketColors = staticCompositionLocalOf { DarkMarket }

object AppTheme {
    val market: MarketColors
        @Composable get() = LocalMarketColors.current
}

@Composable
fun MinhasAcoesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMarketColors provides if (dark) DarkMarket else LightMarket) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
