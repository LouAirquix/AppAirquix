package com.example.airquix01

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class CustomEnv(val name: String, val category: String)

class MainViewModel : ViewModel() {
    val predefinedEnvironments = listOf("Inside", "Outside")
    var customEnvironments by mutableStateOf(listOf<CustomEnv>())
    var currentManualEnvironment by mutableStateOf<String?>(null)

    var loggingEnabledAi = mutableStateOf(false)
    var loggingEnabledManual = mutableStateOf(false)
    var cameraInService = mutableStateOf(false)
    var aiMismatchEnabled = mutableStateOf(true)

    var lastLogTime = 0L

    fun addCustomEnvironment(env: String, category: String) {
        customEnvironments = customEnvironments + CustomEnv(env, category)
    }

    fun setManualEnvironment(env: String?) {
        currentManualEnvironment = env
    }

    fun shouldLog(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLogTime >= 10_000) {
            lastLogTime = now
            return true
        }
        return false
    }

    fun getManualEnvCategory(env: String): String? {
        customEnvironments.forEach {
            if (it.name == env) return it.category
        }
        if (predefinedEnvironments.contains(env)) return env
        return null
    }
}
