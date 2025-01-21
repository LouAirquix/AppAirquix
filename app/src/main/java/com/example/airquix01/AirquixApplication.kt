package com.example.airquix01

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AirquixApplication : Application(), ViewModelStoreOwner {

    private val appViewModelStore = ViewModelStore()
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate() {
        super.onCreate()
        appContext = this
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    fun getMainViewModel(): MainViewModel {
        return mainViewModel
    }

    companion object {
        // Global zugänglicher Context
        lateinit var appContext: Context
            private set
    }
}
