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

    // A) States für Live-Anzeige und Datenhaltung
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

    // B) Logs und CSV-Handling
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
                // Erweitert (34 Spalten) für CSV
                logsCsvFile!!.writeText(
                    "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                            "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                            "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                            "VEHICLE_label,vehicle_conf,VEHICLE_label2,vehicle_conf2,VEHICLE_label3,vehicle_conf3," +
                            "NEW_MODEL_LABEL,NEW_MODEL_CONF,NEW_MODEL_LABEL2,NEW_MODEL_CONF2,NEW_MODEL_LABEL3,NEW_MODEL_CONF3," +
                            "SPEED_m_s,PEGEL\n"
                )
            }
        }
        return logsCsvFile!!
    }

    fun getFeatureCsvFile(): File {
        if (featureCsvFile == null) {
            val dir = AirquixApplication.appContext.getExternalFilesDir(null)
            featureCsvFile = File(dir, featureCsvFileName)
            if (!featureCsvFile!!.exists()) {
                featureCsvFile!!.writeText(
                    "timestamp," +
                            "places_top1,places_top1_conf," +
                            "places_top2,places_top2_conf," +
                            "places_top3,places_top3_conf," +
                            "places_top4,places_top4_conf," +
                            "places_top5,places_top5_conf," +
                            "scene_type_majority," +
                            "scene_type_aggregated_io," +
                            "activity_label,activity_confidence," +
                            "yamnet_top1,avg_conf1," +
                            "yamnet_top2,avg_conf2," +
                            "yamnet_top3,avg_conf3," +
                            "yamnet_global_top1,global_conf1," +
                            "yamnet_global_top2,global_conf2," +
                            "yamnet_single_top1,single_conf1," +
                            "yamnet_single_top2,single_conf2," +
                            "vehicle_top1_agg," +
                            "new_model_output\n"
                )
            }
        }
        return featureCsvFile!!
    }

    // Neue Funktion zum Löschen aller Logs und Zurücksetzen der CSV-Dateien
    fun clearAllLogs() {
        logList.clear()
        val logsFile = getLogsCsvFile()
        if (logsFile.exists()) logsFile.delete()
        logsFile.writeText(
            "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                    "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                    "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                    "VEHICLE_label,vehicle_conf,VEHICLE_label2,vehicle_conf2,VEHICLE_label3,vehicle_conf3," +
                    "NEW_MODEL_LABEL,NEW_MODEL_CONF,NEW_MODEL_LABEL2,NEW_MODEL_CONF2,NEW_MODEL_LABEL3,NEW_MODEL_CONF3," +
                    "SPEED_m_s,PEGEL\n"
        )

        val featureFile = getFeatureCsvFile()
        if (featureFile.exists()) featureFile.delete()
        featureFile.writeText(
            "timestamp," +
                    "places_top1,places_top1_conf," +
                    "places_top2,places_top2_conf," +
                    "places_top3,places_top3_conf," +
                    "places_top4,places_top4_conf," +
                    "places_top5,places_top5_conf," +
                    "scene_type_majority," +
                    "scene_type_aggregated_io," +
                    "activity_label,activity_confidence," +
                    "yamnet_top1,avg_conf1," +
                    "yamnet_top2,avg_conf2," +
                    "yamnet_top3,avg_conf3," +
                    "yamnet_global_top1,global_conf1," +
                    "yamnet_global_top2,global_conf2," +
                    "yamnet_single_top1,single_conf1," +
                    "yamnet_single_top2,single_conf2," +
                    "vehicle_top1_agg," +
                    "new_model_output\n"
        )
    }

    /**
     * Hier werden zwei Strings erzeugt:
     * - displayLine: nur die ursprünglichen 26 Spalten (wie in der alten Log-Formatierung)
     * - csvLine: der vollständige, erweiterte Log (34 Spalten)
     *
     * In der UI wird displayLine verwendet, während csvLine in die Datei geschrieben wird.
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
        pegel: Float
    ) {
        // Erweiterte Daten (für CSV)
        val newModelResults = currentNewModelMultiResults.value
        val newModelLabel2 = if (newModelResults.size > 1) newModelResults[1].label else "none"
        val newModelConf2 = if (newModelResults.size > 1) newModelResults[1].confidence else 0f
        val newModelLabel3 = if (newModelResults.size > 2) newModelResults[2].label else "none"
        val newModelConf3 = if (newModelResults.size > 2) newModelResults[2].confidence else 0f

        val vehicleResults = currentVehicleMultiResults.value
        val vehLabel2 = if (vehicleResults.size > 1) vehicleResults[1].label else "none"
        val vehConf2 = if (vehicleResults.size > 1) vehicleResults[1].confidence else 0f
        val vehLabel3 = if (vehicleResults.size > 2) vehicleResults[2].label else "none"
        val vehConf3 = if (vehicleResults.size > 2) vehicleResults[2].confidence else 0f

        // displayLine – nur die ursprünglich verwendeten 26 Spalten:
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
            // YamNet Top-3
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            append(csvEscape(top1?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top1?.confidence ?: 0f)).append(",")
            append(csvEscape(top2?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top2?.confidence ?: 0f)).append(",")
            append(csvEscape(top3?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top3?.confidence ?: 0f)).append(",")
            // Vehicle: nur Top1
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f)).append(",")
            // New Model: nur Top1
            append(csvEscape(newModelTop.first)).append(",")
            append("%.2f".format(Locale.US, newModelTop.second)).append(",")
            append("%.2f".format(Locale.US, speed)).append(",")
            append("%.2f".format(Locale.US, pegel))
        }

        // csvLine – alle 34 Spalten (erweiterter Log)
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
            // YamNet Top-3
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            append(csvEscape(top1?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top1?.confidence ?: 0f)).append(",")
            append(csvEscape(top2?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top2?.confidence ?: 0f)).append(",")
            append(csvEscape(top3?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top3?.confidence ?: 0f)).append(",")
            // Vehicle: Top1 und zusätzlich zwei weitere
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f)).append(",")
            append(csvEscape(vehLabel2)).append(",")
            append("%.2f".format(Locale.US, vehConf2)).append(",")
            append(csvEscape(vehLabel3)).append(",")
            append("%.2f".format(Locale.US, vehConf3)).append(",")
            // New Model: Top1 und zusätzlich zwei weitere
            append(csvEscape(newModelTop.first)).append(",")
            append("%.2f".format(Locale.US, newModelTop.second)).append(",")
            append(csvEscape(newModelLabel2)).append(",")
            append("%.2f".format(Locale.US, newModelConf2)).append(",")
            append(csvEscape(newModelLabel3)).append(",")
            append("%.2f".format(Locale.US, newModelConf3)).append(",")
            append("%.2f".format(Locale.US, speed)).append(",")
            append("%.2f".format(Locale.US, pegel))
        }
        // Nur die displayLine wird in der UI angezeigt
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

    // Methode zur Aktualisierung der Aktivität
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
