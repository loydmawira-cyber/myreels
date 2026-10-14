package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentColor,
    background = BgColor,
    surface = CardColor,
    onPrimary = TextColor,
    onBackground = TextColor,
    onSurface = TextColor,
    error = DangerColor
)

private val LightColorScheme = lightColorScheme(
    primary = AccentColor,
    background = Color(0xFFF8F8FC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111118),
    onSurface = Color(0xFF111118),
    error = DangerColor
)

@Composable
fun MyReelsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
