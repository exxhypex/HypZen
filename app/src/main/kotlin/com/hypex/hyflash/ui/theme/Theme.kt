package com.hypex.hyflash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    secondary = Info,
    tertiary = Info,
    background = DarkBg,
    surface = Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = Error,
    onError = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondaryDark,
    outline = Border2,
    outlineVariant = Border
)

private val LightColorScheme = lightColorScheme(
    primary = Mint,
    secondary = Info,
    tertiary = Info,
    background = LightBg,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = Error,
    onError = Color.White,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder2,
    outlineVariant = LightBorder
)

@Composable
fun HyflashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val MaterialTheme.isDark: Boolean
    @Composable
    get() = colorScheme.background == DarkBg
