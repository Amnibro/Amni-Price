package com.amniscient.price.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf

/** User preferences that affect rendering everywhere. */
data class UiPrefs(val imperialUnits: Boolean, val haptics: Boolean)

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs(imperialUnits = true, haptics = true) }

/** App-wide snackbar so any screen can confirm an action or offer Undo. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

suspend fun SnackbarHostState.showUndo(message: String, onUndo: suspend () -> Unit) {
    val result = showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** False in screenshot tests so the map doesn't try to download tiles. */
val LocalMapTilesOnline = staticCompositionLocalOf { true }
