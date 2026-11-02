package com.retro.gba.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.retro.gba.data.prefs.UserPreferences

/**
 * Tokens de cores semânticos e calibrados para contraste WCAG AA+ em Dark Mode.
 * Substitui os tons genéricos e não curados ("slop") por uma identidade visual de alto padrão.
 */
@Immutable
data class AppThemeColors(
    val id: String,
    val displayName: String,
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val trophyGold: Color,
    val retroGreen: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

// 1. GBA Índigo Clássico (Padrão Oficial de Fábrica)
val GbaIndigoPalette = AppThemeColors(
    id = UserPreferences.APP_THEME_GBA_INDIGO,
    displayName = "GBA Índigo Clássico",
    background = Color(0xFF0E0C16),
    surface = Color(0xFF171524),
    card = Color(0xFF211E33),
    border = Color(0xFF353052),
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFFA29BFE),
    accent = Color(0xFFA29BFE),
    trophyGold = Color(0xFFF59E0B),
    retroGreen = Color(0xFF10B981),
    textPrimary = Color(0xFFF1F0F7),
    textSecondary = Color(0xFF9E9AA8)
)

// 2. Glacier (GBA Translúcido Gelo)
val GlacierPalette = AppThemeColors(
    id = UserPreferences.APP_THEME_GLACIER,
    displayName = "Glacier Translúcido",
    background = Color(0xFF0A1118),
    surface = Color(0xFF111C28),
    card = Color(0xFF18283A),
    border = Color(0xFF29425E),
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF7DD3FC),
    accent = Color(0xFF38BDF8),
    trophyGold = Color(0xFFFBBF24),
    retroGreen = Color(0xFF34D399),
    textPrimary = Color(0xFFF0F9FF),
    textSecondary = Color(0xFF94A3B8)
)

// 3. SP Platinum (GBA SP Metálico Titânio)
val SpPlatinumPalette = AppThemeColors(
    id = UserPreferences.APP_THEME_SP_PLATINUM,
    displayName = "SP Platinum",
    background = Color(0xFF0F1115),
    surface = Color(0xFF181B22),
    card = Color(0xFF222731),
    border = Color(0xFF363E4E),
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFCBD5E1),
    accent = Color(0xFF60A5FA),
    trophyGold = Color(0xFFF59E0B),
    retroGreen = Color(0xFF10B981),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8)
)

// 4. Famicom 20th Anniversary (Bordô & Ouro Retrô)
val FamicomPalette = AppThemeColors(
    id = UserPreferences.APP_THEME_FAMICOM,
    displayName = "Famicom 20th",
    background = Color(0xFF130B0E),
    surface = Color(0xFF1E1217),
    card = Color(0xFF2B1A22),
    border = Color(0xFF4D2C3B),
    primary = Color(0xFFE11D48),
    secondary = Color(0xFFEAB308),
    accent = Color(0xFFEAB308),
    trophyGold = Color(0xFFEAB308),
    retroGreen = Color(0xFF22C55E),
    textPrimary = Color(0xFFFFF1F2),
    textSecondary = Color(0xFFA89BA0)
)

// 5. OLED Stealth (Preto Absoluto / Máxima Bateria)
val OledStealthPalette = AppThemeColors(
    id = UserPreferences.APP_THEME_OLED_STEALTH,
    displayName = "OLED Stealth",
    background = Color(0xFF000000),
    surface = Color(0xFF101010),
    card = Color(0xFF181818),
    border = Color(0xFF2A2A2A),
    primary = Color(0xFFE4E4E7),
    secondary = Color(0xFFA1A1AA),
    accent = Color(0xFFFFFFFF),
    trophyGold = Color(0xFFF59E0B),
    retroGreen = Color(0xFF10B981),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF71717A)
)

val AvailableAppThemes = listOf(
    GbaIndigoPalette,
    GlacierPalette,
    SpPlatinumPalette,
    FamicomPalette,
    OledStealthPalette
)

fun getAppThemeColors(themeId: String): AppThemeColors {
    return when (themeId) {
        UserPreferences.APP_THEME_GLACIER -> GlacierPalette
        UserPreferences.APP_THEME_SP_PLATINUM -> SpPlatinumPalette
        UserPreferences.APP_THEME_FAMICOM -> FamicomPalette
        UserPreferences.APP_THEME_OLED_STEALTH -> OledStealthPalette
        else -> GbaIndigoPalette
    }
}

// Aliases para compatibilidade reversa com arquivos estáticos existentes
val GbaPrimary get() = GbaIndigoPalette.primary
val GbaSecondary get() = GbaIndigoPalette.secondary
val GbaBackground get() = GbaIndigoPalette.background
val GbaSurface get() = GbaIndigoPalette.surface
val GbaCard get() = GbaIndigoPalette.card
val TrophyGold get() = GbaIndigoPalette.trophyGold
val RetroGreen get() = GbaIndigoPalette.retroGreen
val DpadColor = Color(0xFF2A273B)
