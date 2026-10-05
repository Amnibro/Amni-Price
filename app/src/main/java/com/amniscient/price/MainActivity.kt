package com.amniscient.price

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amniscient.price.ui.AmniPriceRoot
import com.amniscient.price.ui.theme.AmniPriceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmniPriceTheme {
                AmniPriceRoot()
            }
        }
    }
}
