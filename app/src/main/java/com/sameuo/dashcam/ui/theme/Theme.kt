package com.sameuo.dashcam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = SameuoRed,
    onPrimary = White,
    primaryContainer = SameuoRedDark,
    onPrimaryContainer = White,
    secondary = SameuoRedBright,
    background = Black950,
    onBackground = White,
    surface = Black900,
    onSurface = White,
    surfaceVariant = Black800,
    onSurfaceVariant = Grey300,
    surfaceContainer = Black850,
    outline = Grey500,
    error = SameuoRedBright,
)

private val LightColors = lightColorScheme(
    primary = SameuoRed,
    onPrimary = White,
    primaryContainer = SameuoRedDark,
    onPrimaryContainer = White,
    secondary = SameuoRed,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightTextSecondary,
    error = SameuoRed,
)

/**
 * @param dark null = follow system; otherwise forced dark/light (Appearance setting).
 */
@Composable
fun SameuoTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val useDark = dark ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = SameuoTypography,
        content = content,
    )
}
