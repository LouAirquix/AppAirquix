package com.example.airquix01

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.DetectedActivity

data class CustomEnv(val name: String, val category: String)
data class DetectedActivityData(val activityType: String, val confidence: Int)

class MainViewModel : ViewModel() {
    val predefinedEnvironments = listOf("Inside", "Outside")
    var customEnvironments by mutableStateOf(listOf<CustomEnv>())
    var currentManualEnvironment by mutableStateOf<String?>(null)

    var loggingEnabledAi = mutableStateOf(false)
    var loggingEnabledManual = mutableStateOf(false)
    var cameraInService = mutableStateOf(false)
    var aiMismatchEnabled = mutableStateOf(true)

    var lastLogTime = 0L

    // Neue State-Variablen für Activity Recognition
    var detectedActivity by mutableStateOf<DetectedActivityData?>(null)

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

    // Neue Funktion zur Aktualisierung der erkannten Aktivität
    fun updateDetectedActivity(activityType: Int, confidence: Int) {
        val typeString = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
        detectedActivity = DetectedActivityData(typeString, confidence)
    }
}
