package com.example.airquix01

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AirquixApplication : Application(), ViewModelStoreOwner {
    private val appViewModelStore = ViewModelStore()
    private lateinit var viewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun getViewModelStore(): ViewModelStore {
        return appViewModelStore
    }

    fun getViewModel(): MainViewModel {
        return viewModel
    }
}
