package com.sameuo.dashcam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = BgDark,
    secondary = Teal,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnDarkMuted,
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = SurfaceLight,
    secondary = Teal,
    onSecondary = BgDark,
    background = BgLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnLightMuted,
    error = Danger,
)

/**
 * @param darkTheme null = follow system; SAMEUO defaults callers to dark (handlebar legibility).
 */
@Composable
fun SameuoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SameuoTypography,
        content = content,
    )
}
