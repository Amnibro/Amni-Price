package com.amniscient.price.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.amniscient.price.R

// Archivo is the Amniscient brand face (variable: weight 100–900, width 62–125%).
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float = 100f) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val Archivo = FontFamily(archivo(400), archivo(500), archivo(600), archivo(700), archivo(800))

/** Condensed caps for app bars, tabs and eyebrows: the site's "machined" label style. */
val ArchivoCondensed = FontFamily(archivo(500, 80f), archivo(600, 80f), archivo(700, 80f), archivo(800, 80f))

/** Numbers live in mono with tabular figures so prices line up in columns. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object AmniText {
    val eyebrow = TextStyle(
        fontFamily = ArchivoCondensed, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, letterSpacing = 0.12.em, lineHeight = 14.sp,
    )
    val barTitle = TextStyle(
        fontFamily = ArchivoCondensed, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, letterSpacing = 0.14.em,
    )
    val priceLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFeatureSettings = "tnum")
    val price = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFeatureSettings = "tnum")
    val priceSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, fontFeatureSettings = "tnum")
    val hero = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.02).em)
}

private val base = Typography()

val AmniTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineMedium = base.headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineSmall = base.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Archivo),
    bodyMedium = base.bodyMedium.copy(fontFamily = Archivo),
    bodySmall = base.bodySmall.copy(fontFamily = Archivo),
    labelLarge = base.labelLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
    labelMedium = base.labelMedium.copy(fontFamily = ArchivoCondensed, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
    labelSmall = base.labelSmall.copy(fontFamily = ArchivoCondensed, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
)
