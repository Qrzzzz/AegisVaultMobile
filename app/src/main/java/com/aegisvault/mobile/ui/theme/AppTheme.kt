package com.aegisvault.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A7F73),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8B7E72),
    tertiary = Color(0xFFA4767D),
    background = Color(0xFFF7F3EE),
    surface = Color(0xFFFFFCF9),
    surfaceVariant = Color(0xFFEDE4DA),
    onSurface = Color(0xFF2F3532),
    onSurfaceVariant = Color(0xFF6F716D),
    outline = Color(0xFFD4C8BC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8CABB),
    onPrimary = Color(0xFF25302B),
    secondary = Color(0xFFD2C0AF),
    tertiary = Color(0xFFE0B7BE),
    background = Color(0xFF171816),
    surface = Color(0xFF1E201E),
    surfaceVariant = Color(0xFF2A2D2B),
    onSurface = Color(0xFFF1ECE6),
    onSurfaceVariant = Color(0xFFCBC1B7),
    outline = Color(0xFF494C48),
)

@Composable
fun AegisVaultTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
