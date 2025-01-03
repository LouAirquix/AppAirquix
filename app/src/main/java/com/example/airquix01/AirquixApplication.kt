package com.example.airquix01

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AirquixApplication : Application(), ViewModelStoreOwner {
    // Initialisiere das ViewModelStore
    private val appViewModelStore = ViewModelStore()

    // Deklariere das ViewModel
    lateinit var viewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()
        // Initialisiere das ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    // Überschreibe die viewModelStore Eigenschaft
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    // Methode zum Abrufen des ViewModels
    fun getViewModel(): MainViewModel {
        return viewModel
    }
}
