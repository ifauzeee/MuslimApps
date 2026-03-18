package com.example.muslimapps.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMain,
    onPrimary = Color.White,
    primaryContainer = DeepEmerald,
    onPrimaryContainer = EmeraldLight,
    secondary = AmberGold,
    onSecondary = Color.Black,
    background = Color(0xFF011612), // Very dark emerald
    surface = Color(0xFF022C22),
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color.White.copy(alpha = 0.1f)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldMain,
    onPrimary = Color.White,
    primaryContainer = EmeraldLight,
    onPrimaryContainer = DeepEmerald,
    secondary = AmberGold,
    onSecondary = Color.White,
    background = SoftGray,
    surface = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    outline = Color.Black.copy(alpha = 0.05f)
)

@Composable
fun MuslimAppsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for consistent premium branding
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
