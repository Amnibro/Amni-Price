package com.amniscient.price

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amniscient.price.data.ThemeMode
import com.amniscient.price.data.UnitSystem
import com.amniscient.price.domain.UnitParser
import com.amniscient.price.ui.AmniPriceRoot
import com.amniscient.price.ui.components.LocalUiPrefs
import com.amniscient.price.ui.components.UiPrefs
import com.amniscient.price.ui.theme.AmniPriceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as AmniPriceApp).container.settings
        val startOnboarding = !settings.onboarded.value
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle()
            val units by settings.units.collectAsStateWithLifecycle()
            val haptics by settings.haptics.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val imperial = when (units) {
                UnitSystem.AUTO -> UnitParser.defaultImperial()
                UnitSystem.IMPERIAL -> true
                UnitSystem.METRIC -> false
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
                onDispose {}
            }
            AmniPriceTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalUiPrefs provides UiPrefs(imperial, haptics)) {
                    AmniPriceRoot(startOnboarding = startOnboarding)
                }
            }
        }
    }
}
