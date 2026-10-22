package com.amniscient.price

import android.app.Application
import androidx.annotation.StringRes

class AmniPriceApp : Application() {
    lateinit var container: AppContainer
        private set
    companion object { lateinit var instance: AmniPriceApp
        private set }

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        com.amniscient.price.map.MapTiles.init(this)
    }
}
fun tr(@StringRes id: Int, vararg args: Any?): String = if (args.isEmpty()) AmniPriceApp.instance.getString(id) else AmniPriceApp.instance.getString(id, *args)
