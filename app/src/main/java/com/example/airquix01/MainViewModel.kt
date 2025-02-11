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

    // Scene Type (z. B. "indoor" oder "outdoor")
    val currentSceneType = mutableStateOf("Unknown")

    // Aktivität
    data class DetectedActivityData(val activityType: String, val confidence: Int)
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YamNet: Top-3
    data class LabelConfidence(val label: String, val confidence: Float)
    val currentYamnetTop3 = mutableStateOf<List<LabelConfidence>>(emptyList())

    // Vehicle (Top-1) – für die UI-Anzeige
    val currentVehicleTop1 = mutableStateOf<LabelConfidence?>(null)

    // NEU: Ausgabe des neuen Modells (z. B. MobileNetV2)
    val currentNewModelOutput = mutableStateOf<Pair<String, Float>>(Pair("Unknown", 0f))

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
                logsCsvFile!!.writeText(
                    "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                            "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                            "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                            "VEHICLE_label,vehicle_conf,NEW_MODEL_LABEL,NEW_MODEL_CONF\n"
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
                    "VEHICLE_label,vehicle_conf,NEW_MODEL_LABEL,NEW_MODEL_CONF\n"
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
        newModelLabel: String,
        newModelConf: Float
    ) {
        val line = buildString {
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
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            append(csvEscape(top1?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top1?.confidence ?: 0f)).append(",")
            append(csvEscape(top2?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top2?.confidence ?: 0f)).append(",")
            append(csvEscape(top3?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, top3?.confidence ?: 0f)).append(",")
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f)).append(",")
            append(csvEscape(newModelLabel)).append(",")
            append("%.2f".format(Locale.US, newModelConf))
        }
        logList.add(0, line)
        try {
            FileWriter(getLogsCsvFile(), true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Hier könnte auch der 2-Minuten-Aggregator aufgerufen werden, wenn gewünscht.
    }

    private fun csvEscape(str: String?): String {
        if (str == null) return ""
        return if (str.contains(",") || str.contains("\"")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    // Methode zur Aktualisierung der Aktivität (wie bisher)
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
