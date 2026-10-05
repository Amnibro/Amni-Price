package com.amniscient.price

import android.app.Application

class AmniPriceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.amniscient.price.map.MapTiles.init(this)
    }
}
