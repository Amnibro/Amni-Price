package com.amniscient.price.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Amniscient identity: cool graphite, machined brass for chrome and wayfinding, hairlines
 * instead of shadows, 2/3/4 radii. Each Amni app carries its own trade color; Amni-Price's
 * is "receipt lime": the color of a good deal.
 */

@Immutable
data class AmniPalette(
    val brass: Color,
    val brassDim: Color,
    val deal: Color,
    val rise: Color,
    val hairline: Color,
    val panel: Color,
    val panel2: Color,
    val ink: Color,
    val muted: Color,
    /** Categorical chart slots in fixed order, validated for CVD separation on this surface. */
    val series: List<Color>,
    val isDark: Boolean,
)

private val DarkPalette = AmniPalette(
    brass = Color(0xFFC89B4E),
    brassDim = Color(0x1AC89B4E),
    deal = Color(0xFFA3E635),
    rise = Color(0xFFFF7A6B),
    hairline = Color(0xFF20242B),
    panel = Color(0xFF111418),
    panel2 = Color(0xFF161A20),
    ink = Color(0xFFE7E4DC),
    muted = Color(0xFF8E959E),
    series = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181)),
    isDark = true,
)

private val LightPalette = AmniPalette(
    brass = Color(0xFF8A6318),
    brassDim = Color(0x178A6318),
    deal = Color(0xFF3F6212),
    rise = Color(0xFFB91C1C),
    hairline = Color(0xFFE2E0DA),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF8F7F4),
    ink = Color(0xFF101216),
    muted = Color(0xFF5A5F67),
    series = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4)),
    isDark = false,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF0F1114),
    primaryContainer = Color(0xFF263313),
    onPrimaryContainer = Color(0xFFD9F99D),
    secondary = Color(0xFFC89B4E),
    onSecondary = Color(0xFF120C03),
    secondaryContainer = Color(0xFF2A2318),
    onSecondaryContainer = Color(0xFFE2BC7C),
    tertiary = Color(0xFFE2BC7C),
    onTertiary = Color(0xFF120C03),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE7E4DC),
    surface = Color(0xFF0B0D10),
    onSurface = Color(0xFFE7E4DC),
    surfaceVariant = Color(0xFF161A20),
    onSurfaceVariant = Color(0xFF8E959E),
    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF0D0F12),
    surfaceContainer = Color(0xFF111418),
    surfaceContainerHigh = Color(0xFF161A20),
    surfaceContainerHighest = Color(0xFF1C2128),
    outline = Color(0xFF2E343D),
    outlineVariant = Color(0xFF20242B),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF1A0503),
    inverseSurface = Color(0xFFE7E4DC),
    inverseOnSurface = Color(0xFF111418),
    inversePrimary = Color(0xFF3F6212),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6212),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F5C6),
    onPrimaryContainer = Color(0xFF1A2E05),
    secondary = Color(0xFF8A6318),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7CF),
    onSecondaryContainer = Color(0xFF3A2905),
    tertiary = Color(0xFFA2762A),
    onTertiary = Color.White,
    background = Color(0xFFF3F2EF),
    onBackground = Color(0xFF101216),
    surface = Color(0xFFF3F2EF),
    onSurface = Color(0xFF101216),
    surfaceVariant = Color(0xFFEAE9E4),
    onSurfaceVariant = Color(0xFF5A5F67),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F7F4),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF8F7F4),
    surfaceContainerHighest = Color(0xFFEAE9E4),
    outline = Color(0xFFC9C6BE),
    outlineVariant = Color(0xFFE2E0DA),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    inverseSurface = Color(0xFF111418),
    inverseOnSurface = Color(0xFFE7E4DC),
    inversePrimary = Color(0xFFA3E635),
)

val AmniShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

val LocalAmni = staticCompositionLocalOf { DarkPalette }

object Amni {
    val palette: AmniPalette
        @Composable get() = LocalAmni.current
}

@Composable
fun AmniPriceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAmni provides if (darkTheme) DarkPalette else LightPalette) {
        val colors = if (darkTheme) DarkColors else LightColors
        MaterialTheme(colorScheme = colors, typography = AmniTypography, shapes = AmniShapes) {
            // Text and icons outside a Surface still get ink, never the platform's default black.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}
