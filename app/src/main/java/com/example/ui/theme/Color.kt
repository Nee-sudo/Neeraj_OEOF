package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object ThemeState {
    var mode: AppThemeMode = AppThemeMode.LIGHT
    val isLight: Boolean
        get() = mode == AppThemeMode.MINIMALIST_SLATE_LIGHT || mode == AppThemeMode.LIGHT
    val isBlueLight: Boolean
        get() = mode == AppThemeMode.LIGHT
}

// Royal Tech Palette
val DeepOceanSapphire: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFF1F1A24)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFF030712)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFFF8FAFC)
        AppThemeMode.DARK -> Color(0xFF070B19)
        AppThemeMode.LIGHT -> Color(0xFFF0F6FC)
    }

val CharcoalObsidian: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFF130F17)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFF010204)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFFE2E8F0)
        AppThemeMode.DARK -> Color(0xFF020408)
        AppThemeMode.LIGHT -> Color(0xFFD0E1FD)
    }

val RegalGold: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFFFBBF24)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFFD4AF37)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF0F172A)
        AppThemeMode.DARK -> Color(0xFFD4AF37)
        AppThemeMode.LIGHT -> Color(0xFF1E40AF)
    }

val LustrousAmber: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFFED8936)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFFF59E0B)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF475569)
        AppThemeMode.DARK -> Color(0xFFF59E0B)
        AppThemeMode.LIGHT -> Color(0xFF2563EB)
    }

val VelvetCard: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFF2D2435)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFF111827)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFFFFFFFF)
        AppThemeMode.DARK -> Color(0xFF0B1126)
        AppThemeMode.LIGHT -> Color(0xFFFFFFFF)
    }

val ElectricBlue: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFF9F7AEA)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFF3B82F6)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF0284C7)
        AppThemeMode.DARK -> Color(0xFF3B82F6)
        AppThemeMode.LIGHT -> Color(0xFF3B82F6)
    }

val MutedSlate: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFF9E8EAC)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFF6B7280)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF64748B)
        AppThemeMode.DARK -> Color(0xFF64748B)
        AppThemeMode.LIGHT -> Color(0xFF475569)
    }

val GhostWhite: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFFFFF5F5)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFFF9FAFB)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF0F172A)
        AppThemeMode.DARK -> Color(0xFFF1F5F9)
        AppThemeMode.LIGHT -> Color(0xFF0F2942)
    }

val LightGold: Color
    get() = when (ThemeState.mode) {
        AppThemeMode.TWILIGHT_DUSK -> Color(0xFFFFFBEB)
        AppThemeMode.SPACE_ABYSS_DARK -> Color(0xFFFFFBEB)
        AppThemeMode.MINIMALIST_SLATE_LIGHT -> Color(0xFF0F172A)
        AppThemeMode.DARK -> Color(0xFFFDE047)
        AppThemeMode.LIGHT -> Color(0xFF1E40AF)
    }

val CrimsonRep = Color(0xFFEF4444)         // Negative reputation report/alert red
val EmeraldSuccess = Color(0xFF10B981)     // Verified/Upward trend indicators


