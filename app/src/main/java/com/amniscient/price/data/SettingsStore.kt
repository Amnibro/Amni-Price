package com.amniscient.price.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small persisted preferences. The current store makes rapid in-aisle scanning one tap per item. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _currentStoreId = MutableStateFlow(prefs.getLong(KEY_STORE, -1L).takeIf { it > 0 })
    val currentStoreId: StateFlow<Long?> = _currentStoreId.asStateFlow()

    fun setCurrentStore(id: Long?) {
        prefs.edit().putLong(KEY_STORE, id ?: -1L).apply()
        _currentStoreId.value = id
    }

    private companion object {
        const val KEY_STORE = "current_store_id"
    }
}
