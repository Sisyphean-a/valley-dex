package com.example.stardewoffline.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF23633F),
    onPrimary = Color.White,
    secondary = Color(0xFF9A5E00),
    onSecondary = Color.White,
    tertiary = Color(0xFF805015),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDE7),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD7A5),
    onPrimary = Color(0xFF003919),
    secondary = Color(0xFFFFB95D),
    onSecondary = Color(0xFF4F2E00),
    tertiary = Color(0xFFF2BE82),
    background = Color(0xFF111512),
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF181D19),
    surfaceVariant = Color(0xFF343B34),
    error = Color(0xFFFFB4AB),
)

@Composable
fun StardewOfflineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
            }
            darkTheme -> DarkColors
            else -> LightColors
        },
        content = content,
    )
}
