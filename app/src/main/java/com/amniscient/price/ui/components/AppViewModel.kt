package com.amniscient.price.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.amniscient.price.AmniPriceApp
import com.amniscient.price.AppContainer

/** `val vm = appViewModel { FooViewModel(it.repository) }` */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create((this[APPLICATION_KEY] as AmniPriceApp).container) }
        },
    )
