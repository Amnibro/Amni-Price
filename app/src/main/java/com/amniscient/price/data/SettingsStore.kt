package com.amniscient.price.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class UnitSystem(val label: String) { AUTO("Automatic"), IMPERIAL("Imperial (oz, fl oz)"), METRIC("Metric (100 g, 100 ml)") }

/** Small persisted preferences, each exposed as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _currentStoreId = MutableStateFlow(prefs.getLong(KEY_STORE, -1L).takeIf { it > 0 })
    /** The store you're shopping in; makes in-aisle scanning one tap per item. */
    val currentStoreId: StateFlow<Long?> = _currentStoreId.asStateFlow()

    private val _theme = MutableStateFlow(enumPref(KEY_THEME, ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _units = MutableStateFlow(enumPref(KEY_UNITS, UnitSystem.AUTO))
    val units: StateFlow<UnitSystem> = _units.asStateFlow()

    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setCurrentStore(id: Long?) {
        prefs.edit().putLong(KEY_STORE, id ?: -1L).apply()
        _currentStoreId.value = id
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setUnits(units: UnitSystem) {
        prefs.edit().putString(KEY_UNITS, units.name).apply()
        _units.value = units
    }

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _haptics.value = enabled
    }

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        _onboarded.value = true
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private companion object {
        const val KEY_STORE = "current_store_id"
        const val KEY_THEME = "theme"
        const val KEY_UNITS = "units"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ONBOARDED = "onboarded"
    }
}
