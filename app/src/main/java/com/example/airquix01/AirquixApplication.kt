package com.example.airquix01

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AirquixApplication : Application(), ViewModelStoreOwner {
    private val appViewModelStore = ViewModelStore()
    lateinit var viewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    // Umbenannte Methode zur Vermeidung des Konflikts
    fun getMainViewModel(): MainViewModel {
        return viewModel
    }
}
