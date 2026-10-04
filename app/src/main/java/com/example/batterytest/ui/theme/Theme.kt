package com.example.batterytest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFB8C7DC),
    tertiary = Color(0xFF80CBC4),
    background = Color(0xFF121316),
    surface = Color(0xFF1E2024),
    onBackground = Color(0xFFF1F3F4),
    onSurface = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFFB8BCC4),
    outline = Color(0xFF727780)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1689FF),
    secondary = Color(0xFF536579),
    tertiary = Color(0xFF00796B),
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191B1F),
    onSurface = Color(0xFF191B1F),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFF8A9099)
)

@Composable
fun BatteryTestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
