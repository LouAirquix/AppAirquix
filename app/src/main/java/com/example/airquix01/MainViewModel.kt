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
import kotlin.math.abs

class MainViewModel : ViewModel() {

    // -------------------------------------------
    // A) States für Live-Anzeige und Datenhaltung
    // -------------------------------------------
    val isLogging = mutableStateOf(false)

    // Places365 (AlexNet) Top-5
    val currentPlacesTop1 = mutableStateOf("Unknown")
    val currentPlacesTop1Confidence = mutableStateOf(0f)
    val currentPlacesTop2 = mutableStateOf("Unknown")
    val currentPlacesTop2Confidence = mutableStateOf(0f)
    val currentPlacesTop3 = mutableStateOf("Unknown")
    val currentPlacesTop3Confidence = mutableStateOf(0f)
    val currentPlacesTop4 = mutableStateOf("Unknown")
    val currentPlacesTop4Confidence = mutableStateOf(0f)
    val currentPlacesTop5 = mutableStateOf("Unknown")
    val currentPlacesTop5Confidence = mutableStateOf(0f)

    // Scene Type (z. B. "indoor" oder "outdoor")
    val currentSceneType = mutableStateOf("Unknown")

    // Aktivität
    data class DetectedActivityData(val activityType: String, val confidence: Int)
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YamNet: Top-3
    data class LabelConfidence(val label: String, val confidence: Float)
    val currentYamnetTop3 = mutableStateOf<List<LabelConfidence>>(emptyList())

    // Vehicle Audio – für die UI-Anzeige
    val currentVehicleTop1 = mutableStateOf<LabelConfidence?>(null)
    // NEU: Zusätzliche Fahrzeug-Ergebnisse (Top-3)
    val currentVehicleMultiResults = mutableStateOf<List<LabelConfidence>>(emptyList())

    // NEU: Ausgabe des neuen Modells (z. B. MobileNetV2) – Top-1
    val currentNewModelOutput = mutableStateOf<Pair<String, Float>>(Pair("Unknown", 0f))
    // NEU: Zusätzlich alle (Top-3) Ergebnisse des neuen Modells
    val currentNewModelMultiResults = mutableStateOf<List<LabelConfidence>>(emptyList())

    // NEU: Aktuelle Geschwindigkeit in m/s (von GPS-Daten)
    val currentSpeed = mutableStateOf(0f)

    // NEU: Aktueller Pegel (z. B. in dB)
    val currentPegel = mutableStateOf(0f)

    // NEU: Neuer State für den manuell ausgewählten Status (wird im CSV-Log berücksichtigt)
    val currentStatusGt = mutableStateOf("Unknown")

    // -------------------------------------------
    // B) Logs und CSV-Handling
    // -------------------------------------------
    val logList = mutableStateListOf<String>()

    private val logsCsvFileName = "all_in_one_logs.csv"
    private val featureCsvFileName = "feature_vectors.csv"
    private var logsCsvFile: File? = null
    private var featureCsvFile: File? = null

    fun getLogsCsvFile(): File {
        if (logsCsvFile == null) {
            val dir = AirquixApplication.appContext.getExternalFilesDir(null)
            logsCsvFile = File(dir, logsCsvFileName)
            if (!logsCsvFile!!.exists()) {
                // Header für Logs (29 Spalten – wie in der ursprünglichen Version)
                logsCsvFile!!.writeText(
                    "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                            "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                            "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence,status_gt," +
                            "YAMNET_top1,YAMNET_conf_1,YAMNET_top2,YAMNET_conf_2,YAMNET_top3,YAMNET_conf_3," +
                            "VEHICLE_audio_1,vehicle_audio_conf_1,VEHICLE_audio_2,vehicle_audio_conf_2," +
                            "VEHICLE_audio_3,vehicle_audio_conf_3,VEHICLE_image_1,vehicle_image_conf_1," +
                            "VEHICLE_image_2,vehicle_image_conf_2,VEHICLE_image_3,vehicle_image_conf_3," +
                            "speed_m_s,noise_dB\n"
                )
            }
        }
        return logsCsvFile!!
    }

    fun clearAllLogs() {
        logList.clear()
        val logsFile = getLogsCsvFile()
        if (logsFile.exists()) logsFile.delete()
        logsFile.writeText(
            "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                    "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                    "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence,status_gt," +
                    "YAMNET_top1,YAMNET_conf_1,YAMNET_top2,YAMNET_conf_2,YAMNET_top3,YAMNET_conf_3," +
                    "VEHICLE_audio_1,vehicle_audio_conf_1,VEHICLE_audio_2,vehicle_audio_conf_2," +
                    "VEHICLE_audio_3,vehicle_audio_conf_3,VEHICLE_image_1,vehicle_image_conf_1," +
                    "VEHICLE_image_2,vehicle_image_conf_2,VEHICLE_image_3,vehicle_image_conf_3," +
                    "speed_m_s,noise_dB\n"
        )
    }

    /**
     * Erzeugt zwei Strings:
     * - displayLine: Diese Zeile (26 Spalten) wird in der UI angezeigt – unverändert wie zuvor.
     * - csvLine: Der vollständige Log-Eintrag (32 Spalten) enthält zusätzlich das neue Feld status_gt
     *   sowie die Fahrzeugbild-Daten.
     */
    fun appendLog(
        timeStr: String,
        placesTop1: String,
        placesTop1Conf: Float,
        placesTop2: String,
        placesTop2Conf: Float,
        placesTop3: String,
        placesTop3Conf: Float,
        placesTop4: String,
        placesTop4Conf: Float,
        placesTop5: String,
        placesTop5Conf: Float,
        sceneType: String,
        act: String,
        actConf: Int,
        yamTop3: List<LabelConfidence>,
        veh: LabelConfidence?,
        newModelTop: Pair<String, Float>,
        speed: Float,
        pegel: Float,
        status_gt: String  // Neuer Parameter
    ) {
        // displayLine – Nur die ursprünglich verwendeten 26 Spalten (ohne status_gt und Fahrzeugbild-Daten)
        val displayLine = buildString {
            append(csvEscape(timeStr)).append(",")
            append(csvEscape(placesTop1)).append(",")
            append("%.2f".format(Locale.US, placesTop1Conf)).append(",")
            append(csvEscape(placesTop2)).append(",")
            append("%.2f".format(Locale.US, placesTop2Conf)).append(",")
            append(csvEscape(placesTop3)).append(",")
            append("%.2f".format(Locale.US, placesTop3Conf)).append(",")
            append(csvEscape(placesTop4)).append(",")
            append("%.2f".format(Locale.US, placesTop4Conf)).append(",")
            append(csvEscape(placesTop5)).append(",")
            append("%.2f".format(Locale.US, placesTop5Conf)).append(",")
            append(csvEscape(sceneType)).append(",")
            append(csvEscape(act)).append(",")
            append(actConf).append(",")
            // YamNet Top-3 (6 Spalten)
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            append(csvEscape(top1?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top1?.confidence ?: 0f)).append(",")
            append(csvEscape(top2?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top2?.confidence ?: 0f)).append(",")
            append(csvEscape(top3?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top3?.confidence ?: 0f)).append(",")
            // Vehicle Audio: Nur Top1 (2 Spalten)
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f)).append(",")
            // New Model: Nur Top1 (2 Spalten)
            append(csvEscape(newModelTop.first)).append(",")
            append("%.2f".format(Locale.US, newModelTop.second)).append(",")
            // Speed und Noise (je 1 Spalte)
            append("%.2f".format(Locale.US, speed)).append(",")
            append("%.2f".format(Locale.US, pegel))
        }

        // csvLine – Alle 32 Spalten (erweiterter Log)
        val csvLine = buildString {
            append(csvEscape(timeStr)).append(",")
            append(csvEscape(placesTop1)).append(",")
            append("%.2f".format(Locale.US, placesTop1Conf)).append(",")
            append(csvEscape(placesTop2)).append(",")
            append("%.2f".format(Locale.US, placesTop2Conf)).append(",")
            append(csvEscape(placesTop3)).append(",")
            append("%.2f".format(Locale.US, placesTop3Conf)).append(",")
            append(csvEscape(placesTop4)).append(",")
            append("%.2f".format(Locale.US, placesTop4Conf)).append(",")
            append(csvEscape(placesTop5)).append(",")
            append("%.2f".format(Locale.US, placesTop5Conf)).append(",")
            append(csvEscape(sceneType)).append(",")
            append(csvEscape(act)).append(",")
            append(actConf).append(",")
            // Neuer Eintrag für den Status:
            append(csvEscape(status_gt)).append(",")
            // YamNet Top-3 (6 Spalten)
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            append(csvEscape(top1?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top1?.confidence ?: 0f)).append(",")
            append(csvEscape(top2?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top2?.confidence ?: 0f)).append(",")
            append(csvEscape(top3?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top3?.confidence ?: 0f)).append(",")
            // Vehicle Audio: Top1 und zusätzlich zwei weitere (insgesamt 6 Spalten)
            val vehLabel2 = if (currentVehicleMultiResults.value.size > 1)
                currentVehicleMultiResults.value[1].label else "none"
            val vehConf2 = if (currentVehicleMultiResults.value.size > 1)
                currentVehicleMultiResults.value[1].confidence else 0f
            val vehLabel3 = if (currentVehicleMultiResults.value.size > 2)
                currentVehicleMultiResults.value[2].label else "none"
            val vehConf3 = if (currentVehicleMultiResults.value.size > 2)
                currentVehicleMultiResults.value[2].confidence else 0f
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f)).append(",")
            append(csvEscape(vehLabel2)).append(",")
            append("%.2f".format(Locale.US, vehConf2)).append(",")
            append(csvEscape(vehLabel3)).append(",")
            append("%.2f".format(Locale.US, vehConf3)).append(",")
            // New Model: Top1 und zusätzlich zwei weitere (insgesamt 6 Spalten)
            val new2 = if (currentNewModelMultiResults.value.size > 1)
                currentNewModelMultiResults.value[1] else LabelConfidence("none", 0f)
            val new3 = if (currentNewModelMultiResults.value.size > 2)
                currentNewModelMultiResults.value[2] else LabelConfidence("none", 0f)
            append(csvEscape(newModelTop.first)).append(",")
            append("%.2f".format(Locale.US, newModelTop.second)).append(",")
            append(csvEscape(new2.label)).append(",")
            append("%.2f".format(Locale.US, new2.confidence)).append(",")
            append(csvEscape(new3.label)).append(",")
            append("%.2f".format(Locale.US, new3.confidence)).append(",")
            // Speed und Noise (2 Spalten)
            append("%.2f".format(Locale.US, speed)).append(",")
            append("%.2f".format(Locale.US, pegel))
        }
        // Nur displayLine wird in der UI angezeigt (unverändert wie in der ursprünglichen Version)
        logList.add(0, displayLine)
        try {
            FileWriter(getLogsCsvFile(), true).use { writer ->
                writer.appendLine(csvLine)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun csvEscape(str: String?): String {
        if (str == null) return ""
        return if (str.contains(",") || str.contains("\"")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    // -------------------------------------------
    // Aktualisierung der Aktivität im ViewModel
    // -------------------------------------------
    fun updateDetectedActivity(activityType: Int, confidence: Int) {
        val typeString = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "In Vehicle"
            DetectedActivity.ON_BICYCLE -> "On Bicycle"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.TILTING -> "Tilting"
            DetectedActivity.WALKING -> "Walking"
            else -> "Unknown"
        }
        detectedActivity.value = DetectedActivityData(typeString, confidence)
        Log.d("MainViewModel", "Detected Activity: $typeString ($confidence%)")
    }
}
