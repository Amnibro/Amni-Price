package com.amniscient.price.ui.theme

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

private val Green = Color(0xFF1B6B4A)
private val GreenLight = Color(0xFF8FD8B0)
private val Gold = Color(0xFFF5C542)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EEDB),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4E6356),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA0),
    background = Color(0xFFF7FAF5),
    surface = Color(0xFFF7FAF5),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFC9EEDB),
    secondary = Color(0xFFB4CCBC),
    tertiary = Gold,
    tertiaryContainer = Color(0xFF5C4300),
)

/** Color for "this is the cheapest" highlights, consistent across the app. */
val DealColor = Color(0xFF1E8E3E)

@Composable
fun AmniPriceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
