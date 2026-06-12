package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    TWILIGHT_DUSK, SPACE_ABYSS_DARK, MINIMALIST_SLATE_LIGHT, DARK, LIGHT
}

private val TwilightDuskScheme = darkColorScheme(
    primary = Color(0xFFFBBF24), // Warm Sunset Gold
    secondary = Color(0xFFED8936), // Vibrant Clay Orange
    tertiary = Color(0xFF9F7AEA), // Serenely Warm Lavender
    background = Color(0xFF1F1A24), // Dusky Warm Purple
    surface = Color(0xFF2D2435), // Elegant Plum Card
    onPrimary = Color(0xFF1E1428),
    onSecondary = Color(0xFF1E1428),
    onTertiary = Color(0xFFFFF5F5),
    onBackground = Color(0xFFFFF5F5),
    onSurface = Color(0xFFFFF5F5),
    error = Color(0xFFFEB2B2)
)

private val SpaceAbyssDarkScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    secondary = Color(0xFF3B82F6),
    tertiary = Color(0xFF8B5CF6), // Cosmic Purple
    background = Color(0xFF030712), // Abyss Black
    surface = Color(0xFF111827), // Deep Slate/Blue Card
    onPrimary = Color(0xFF030712),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFF1F5F9),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = Color(0xFFEF4444)
)

private val MinimalistSlateLightScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0F172A), // Slate 900
    secondary = Color(0xFF475569), // Slate 600
    tertiary = Color(0xFF0284C7), // Sky Blue
    background = Color(0xFFF8FAFC), // Slate 50
    surface = Color(0xFFFFFFFF), // White Card
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF0F172A),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFEF4444)
)

private val ClassicDarkScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFF3B82F6),
    background = Color(0xFF070B19),
    surface = Color(0xFF0B1126),
    onPrimary = Color(0xFF020408),
    onSecondary = Color(0xFF020408),
    onTertiary = Color(0xFFF1F5F9),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    error = Color(0xFFEF4444)
)

private val LightBlueWhiteScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF1E40AF), // Royal Blue
    secondary = Color(0xFF2563EB), // Blue Accent
    tertiary = Color(0xFF3B82F6),
    background = Color(0xFFF0F6FC), // Light Blue-White
    surface = Color(0xFFFFFFFF), // White Card
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF0F2942),
    onBackground = Color(0xFF0F2942),
    onSurface = Color(0xFF0F2942),
    error = Color(0xFFEF4444)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    ThemeState.mode = themeMode
    val colors = when (themeMode) {
        AppThemeMode.TWILIGHT_DUSK -> TwilightDuskScheme
        AppThemeMode.SPACE_ABYSS_DARK -> SpaceAbyssDarkScheme
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> MinimalistSlateLightScheme
        AppThemeMode.DARK -> ClassicDarkScheme
        AppThemeMode.LIGHT -> LightBlueWhiteScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
