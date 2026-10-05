package com.amniscient.price

import android.content.Context
import com.amniscient.price.data.AppDatabase
import com.amniscient.price.data.PriceRepository
import com.amniscient.price.data.SettingsStore
import com.amniscient.price.scan.ScanSession
import com.amniscient.price.scan.VisionEngine

/** Manual dependency container. Add new app-wide services here. */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)
    val repository = PriceRepository(database)
    val settings = SettingsStore(context)
    val vision by lazy { VisionEngine() }
    val scanSession = ScanSession()
}
