package com.example.airquix01

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.DetectedActivity

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

    // Private MutableState für Activity Recognition
    private val _detectedActivity = mutableStateOf<DetectedActivityData?>(null)
    val detectedActivity: State<DetectedActivityData?> get() = _detectedActivity

    fun addCustomEnvironment(env: String, category: String) {
        customEnvironments = customEnvironments + CustomEnv(env, category)
    }

    fun setManualEnvironment(env: String?) {
        currentManualEnvironment = env
    }

    fun shouldLog(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLogTime >= 10000) { // 10 Sekunden für Testzwecke
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

    // Funktion zur Aktualisierung der erkannten Aktivität
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
        _detectedActivity.value = DetectedActivityData(typeString, confidence)
        Log.d("MainViewModel", "Detected Activity: $typeString with confidence $confidence%")
    }
}
