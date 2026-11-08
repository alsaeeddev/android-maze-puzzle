package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = RetroCyan,
    onPrimary = Color.Black,
    primaryContainer = RetroDarkSurfaceVariant,
    onPrimaryContainer = RetroCyan,
    secondary = RetroEmerald,
    onSecondary = Color.Black,
    tertiary = RetroAmber,
    onTertiary = Color.Black,
    background = RetroDarkBackground,
    onBackground = Color.White,
    surface = RetroDarkSurface,
    onSurface = Color.White,
    surfaceVariant = RetroDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4CBD4),
    outline = RetroDarkBorder,
    outlineVariant = Color(0xFF3B4455),
    error = RetroCoral,
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007A87),
    onPrimary = Color.White,
    primaryContainer = RetroLightSurfaceVariant,
    onPrimaryContainer = Color(0xFF00383E),
    secondary = Color(0xFF008947),
    onSecondary = Color.White,
    tertiary = Color(0xFFB87800),
    onTertiary = Color.White,
    background = RetroLightBackground,
    onBackground = Color(0xFF1F2328),
    surface = RetroLightSurface,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = RetroLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF57606A),
    outline = RetroLightBorder,
    outlineVariant = Color(0xFFD8DEE4),
    // neon coral is hard to read on the light surface; use a deeper red there
    error = Color(0xFFC62828),
    onError = Color.White
)

@Composable
fun AsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
