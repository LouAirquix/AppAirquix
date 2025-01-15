package com.example.airquix01

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.DetectedActivity

/**
 * Datenklasse zur Speicherung erkannter Aktivitäten.
 */
data class DetectedActivityData(val activityType: String, val confidence: Int)

/**
 * Datenklasse zur Speicherung von Aktivitäts-Log-Einträgen.
 */
data class ActivityLogEntry(
    val activityType: String,
    val confidence: Int,
    val timestamp: Long
)

/**
 * Datenklasse zur Definition benutzerdefinierter Umgebungen.
 */

/**
 * MainViewModel verwaltet den Zustand der Anwendung, einschließlich Umgebungs- und Aktivitätsdaten.
 */
class MainViewModel : ViewModel() {

    // ---------------------------
    // Vordefinierte und Benutzerdefinierte Umgebungen
    // ---------------------------

    /**
     * Liste der vordefinierten Umgebungen.
     */
    val predefinedEnvironments = listOf("Inside", "Outside", "In Car")

    /**
     * Liste der benutzerdefinierten Umgebungen.
     */
    var customEnvironments by mutableStateOf(listOf<CustomEnv>())

    /**
     * Aktuell manuell ausgewählte Umgebung.
     */
    var currentManualEnvironment by mutableStateOf<String?>(null)

    // ---------------------------
    // Logging Flags und Service Status
    // ---------------------------

    /**
     * Flag für das AI-Logging.
     */
    var loggingEnabledAi = mutableStateOf(false)

    /**
     * Flag für das manuelle Logging.
     */
    var loggingEnabledManual = mutableStateOf(false)

    /**
     * Flag, ob der Kamera-Foreground-Service läuft.
     */
    var cameraInService = mutableStateOf(false)

    /**
     * Flag, ob der Mismatch-Assistent aktiviert ist.
     */
    var aiMismatchEnabled = mutableStateOf(true)

    // ---------------------------
    // Zeitstempel für Logging
    // ---------------------------

    /**
     * Letzter Log-Zeitpunkt zur Throttling.
     */
    var lastLogTime = 0L

    // ---------------------------
    // Aktivitätserkennung (Activity Recognition)
    // ---------------------------

    /**
     * Zustand der zuletzt erkannten Aktivität.
     */
    private val _detectedActivity = mutableStateOf<DetectedActivityData?>(null)
    val detectedActivity: State<DetectedActivityData?> get() = _detectedActivity

    /**
     * Liste aller Aktivitäts-Log-Einträge.
     */
    private val _activityLogs = mutableStateListOf<ActivityLogEntry>()
    val activityLogs: List<ActivityLogEntry> get() = _activityLogs

    /**
     * Aktualisiert die erkannte Aktivität und fügt einen Log-Eintrag hinzu.
     *
     * @param activityType Der erkannte Aktivitätstyp als Integer.
     * @param confidence Die Confidence der Erkennung.
     */
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

        // Füge den Log-Eintrag hinzu
        val logEntry = ActivityLogEntry(
            activityType = typeString,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
        _activityLogs.add(logEntry)
    }

    /**
     * Löscht alle Aktivitäts-Logs.
     */
    fun clearActivityLogs() {
        _activityLogs.clear()
        Log.d("MainViewModel", "All activity logs cleared.")
    }

    // ---------------------------
    // ML Kit AI-Umgebung
    // ---------------------------

    /**
     * Zustand der zuletzt erkannten AI-Umgebung.
     */
    private val _lastAiEnv = mutableStateOf("Unknown")
    val lastAiEnv: State<String> get() = _lastAiEnv

    /**
     * Aktualisiert die erkannte AI-Umgebung.
     *
     * @param env Die erkannte Umgebung als String.
     */
    fun updateAiEnv(env: String) {
        _lastAiEnv.value = env
        Log.d("MainViewModel", "AI Environment updated to: $env")
    }

    // ---------------------------
    // YamNet Audio-Klassifikation
    // ---------------------------

    /**
     * Zustand des zuletzt erkannten YamNet-Labels.
     */
    private val _lastYamNetLabel = mutableStateOf("No classification")
    val lastYamNetLabel: State<String> get() = _lastYamNetLabel

    /**
     * Zustand der Confidence des zuletzt erkannten YamNet-Labels.
     */
    private val _lastYamNetConfidence = mutableStateOf(0f)
    val lastYamNetConfidence: State<Float> get() = _lastYamNetConfidence

    /**
     * Aktualisiert das erkannte YamNet-Label und die Confidence.
     *
     * @param label Das erkannte Label als String.
     * @param confidence Die Confidence des Labels als Float.
     */
    fun updateYamNetResult(label: String, confidence: Float) {
        _lastYamNetLabel.value = label
        _lastYamNetConfidence.value = confidence
        Log.d("MainViewModel", "YamNet Result updated to: $label with confidence $confidence")
    }

    // ---------------------------
    // Benutzerdefinierte Umgebungen
    // ---------------------------

    /**
     * Fügt eine neue benutzerdefinierte Umgebung hinzu.
     *
     * @param env Der Name der Umgebung.
     * @param category Die Kategorie der Umgebung.
     */
    fun addCustomEnvironment(env: String, category: String) {
        customEnvironments = customEnvironments + CustomEnv(env, category)
        Log.d("MainViewModel", "Added custom environment: $env in category: $category")
    }

    /**
     * Setzt die manuell ausgewählte Umgebung.
     *
     * @param env Die ausgewählte Umgebung oder null.
     */
    fun setManualEnvironment(env: String?) {
        currentManualEnvironment = env
        Log.d("MainViewModel", "Manual environment set to: $env")
    }

    /**
     * Holt die Kategorie einer manuellen Umgebung.
     *
     * @param env Der Name der Umgebung.
     * @return Die Kategorie oder null, wenn nicht gefunden.
     */
    fun getManualEnvCategory(env: String): String? {
        customEnvironments.forEach {
            if (it.name.equals(env, ignoreCase = true)) return it.category
        }
        if (predefinedEnvironments.contains(env)) return env
        return null
    }

    // ---------------------------
    // Logging-Funktionen
    // ---------------------------

    /**
     * Prüft, ob geloggt werden sollte basierend auf dem letzten Log-Zeitpunkt.
     *
     * @return True, wenn geloggt werden sollte, sonst False.
     */
    fun shouldLog(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLogTime >= 10000) { // 10 Sekunden
            lastLogTime = now
            return true
        }
        return false
    }
}
