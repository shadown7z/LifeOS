package com.atlaes.lifeos

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B87),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EDF1),
    onPrimaryContainer = Color(0xFF062F3D),
    secondary = Color(0xFF42736F),
    secondaryContainer = Color(0xFFD5ECE7),
    onSecondaryContainer = Color(0xFF0B302D),
    background = Color(0xFFF7F9F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EFEC),
    onSurface = Color(0xFF17201E),
    onSurfaceVariant = Color(0xFF596662),
    outline = Color(0xFF84938E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82C9D2),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF124E5C),
    onPrimaryContainer = Color(0xFFC4F1F5),
    secondary = Color(0xFF9AD1C6),
    secondaryContainer = Color(0xFF28504B),
    onSecondaryContainer = Color(0xFFB7EBE1),
    background = Color(0xFF101719),
    surface = Color(0xFF18211F),
    surfaceVariant = Color(0xFF303B38),
    onSurface = Color(0xFFE1E9E5),
    onSurfaceVariant = Color(0xFFB8C7C1),
    outline = Color(0xFF899B94),
)

@Composable
fun LifeOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
