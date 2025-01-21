package com.example.airquix01

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.DetectedActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel : ViewModel() {

    // Indikator, ob aktuell geloggt wird
    val isLogging = mutableStateOf(false)

    // Kamerabasiertes Environment
    val currentEnvironment = mutableStateOf<String?>(null)
    val currentEnvironmentConfidence = mutableStateOf(0f)

    // Aktivität
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YamNet
    val currentYamnetLabel = mutableStateOf("No sound yet")
    val currentYamnetConfidence = mutableStateOf(0f)

    // Logs (nur zur Anzeige in der UI)
    val logList = mutableStateListOf<String>()

    // Eine einfache Datenklasse für Activity
    data class DetectedActivityData(val activityType: String, val confidence: Int)

    // CSV-Datei
    private val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val csvFileName = "all_in_one_logs.csv"

    private var csvFile: File? = null

    init {
        // Initialisierung kann hier erfolgen, falls nötig
    }

    fun getCsvFile(): File {
        if (csvFile == null) {
            // Erstelle im App-spezifischen ExternalFilesDir
            // (damit wir die FileProvider-Freigabe nutzen können)
            val dir = AirquixApplication.appContext.getExternalFilesDir(null)
            csvFile = File(dir, csvFileName)
            if (!csvFile!!.exists()) {
                // Kopfzeile schreiben
                csvFile!!.writeText("timestamp,ENV,ENV_confidence,ACT,ACT_confidence,YAMNET_label,YAMNET_confidence\n")
            }
        }
        return csvFile!!
    }

    // Wird von ActivityRecognitionReceiver aufgerufen
    fun updateDetectedActivity(activityType: Int, confidence: Int) {
        val typeString = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.TILTING -> "Tilting"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
        detectedActivity.value = DetectedActivityData(typeString, confidence)
        Log.d("MainViewModel", "Detected Activity: $typeString ($confidence%)")
    }

    // Wird vom Service z.B. jede Sekunde aufgerufen
    fun appendLog(line: String) {
        // In Compose-Liste
        logList.add(line)

        // In CSV
        try {
            val file = getCsvFile()
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Optional: Log.d
        Log.d("MainViewModel", "LOGGED -> $line")
    }

    fun clearLogs() {
        logList.clear()
        val file = getCsvFile()
        if (file.exists()) {
            file.delete()
        }
        // neue Kopfzeile
        file.writeText("timestamp,ENV,ENV_confidence,ACT,ACT_confidence,YAMNET_label,YAMNET_confidence\n")
    }
}
