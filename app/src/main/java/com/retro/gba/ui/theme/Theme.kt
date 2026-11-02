package com.retro.gba.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.retro.gba.data.prefs.UserPreferences

val LocalAppThemeColors = staticCompositionLocalOf { GbaIndigoPalette }

val MaterialTheme.retroColors: AppThemeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppThemeColors.current

@Composable
fun RetroGBATheme(
    appTheme: String = UserPreferences.APP_THEME_GBA_INDIGO,
    content: @Composable () -> Unit
) {
    val themeColors = getAppThemeColors(appTheme)

    val colorScheme = darkColorScheme(
        primary = themeColors.primary,
        secondary = themeColors.secondary,
        tertiary = themeColors.trophyGold,
        background = themeColors.background,
        surface = themeColors.surface,
        surfaceVariant = themeColors.card,
        outline = themeColors.border,
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onTertiary = Color.Black,
        onBackground = themeColors.textPrimary,
        onSurface = themeColors.textPrimary,
        onSurfaceVariant = themeColors.textSecondary
    )

    CompositionLocalProvider(LocalAppThemeColors provides themeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
