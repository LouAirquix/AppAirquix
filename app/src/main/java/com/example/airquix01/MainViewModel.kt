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
import kotlin.math.max

class MainViewModel : ViewModel() {

    // Flag, ob Logging aktiv ist
    val isLogging = mutableStateOf(false)

    // Felder für Places365 (AlexNet) Ergebnisse
    val currentPlacesTop1 = mutableStateOf("Unknown")
    val currentPlacesTop1Confidence = mutableStateOf(0f)
    val currentPlacesTop2 = mutableStateOf("Unknown")
    val currentPlacesTop2Confidence = mutableStateOf(0f)
    // Neue Felder für Top-3 und Top-4
    val currentPlacesTop3 = mutableStateOf("Unknown")
    val currentPlacesTop3Confidence = mutableStateOf(0f)
    val currentPlacesTop4 = mutableStateOf("Unknown")
    val currentPlacesTop4Confidence = mutableStateOf(0f)

    // Aktivität
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YamNet: Top-3
    val currentYamnetTop3 = mutableStateOf<List<LabelConfidence>>(emptyList())

    // Vehicle Top-1
    val currentVehicleTop1 = mutableStateOf<LabelConfidence?>(null)

    // Logs (für die UI)
    val logList = mutableStateListOf<String>()

    // CSV-Dateinamen
    private val logsCsvFileName = "all_in_one_logs.csv"
    private val featureCsvFileName = "feature_vectors.csv"
    private var logsCsvFile: File? = null
    private var featureCsvFile: File? = null

    // Aggregator (aggregiert die Places-, Activity- und YamNet-Daten)
    private val aggregator = TwoMinuteAggregator()

    data class DetectedActivityData(val activityType: String, val confidence: Int)
    data class LabelConfidence(val label: String, val confidence: Float)

    private fun csvEscape(str: String?): String {
        if (str == null) return ""
        return if (str.contains(",") || str.contains("\"")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    // Angepasster CSV-Header:
    // 0: timestamp
    // 1: PLACES_top1, 2: places_top1_conf
    // 3: PLACES_top2, 4: places_top2_conf
    // 5: PLACES_top3, 6: places_top3_conf
    // 7: PLACES_top4, 8: places_top4_conf
    // 9: ACT, 10: ACT_confidence
    // 11: YAMNET_top1, 12: top1_conf, 13: YAMNET_top2, 14: top2_conf, 15: YAMNET_top3, 16: top3_conf
    // 17: VEHICLE_label, 18: vehicle_conf
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
        act: String,
        actConf: Int,
        yamTop3: List<LabelConfidence>,
        veh: LabelConfidence?
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
            append(csvEscape(act)).append(",")
            append(actConf).append(",")
            val top1 = yamTop3.getOrNull(0)
            val top2 = yamTop3.getOrNull(1)
            val top3 = yamTop3.getOrNull(2)
            val top1Label = csvEscape(top1?.label ?: "none")
            val top1Conf = "%.2f".format(Locale.US, top1?.confidence ?: 0f)
            val top2Label = csvEscape(top2?.label ?: "none")
            val top2Conf = "%.2f".format(Locale.US, top2?.confidence ?: 0f)
            val top3Label = csvEscape(top3?.label ?: "none")
            val top3Conf = "%.2f".format(Locale.US, top3?.confidence ?: 0f)
            append(top1Label).append(",")
            append(top1Conf).append(",")
            append(top2Label).append(",")
            append(top2Conf).append(",")
            append(top3Label).append(",")
            append(top3Conf).append(",")
            append(csvEscape(veh?.label ?: "none")).append(",")
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f))
        }
        logList.add(0, line)
        try {
            val file = getLogsCsvFile()
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        aggregator.addData(
            places = Pair(
                Pair(placesTop1, placesTop1Conf),
                Pair(placesTop2, placesTop2Conf)
            ),
            act = detectedActivity.value,
            yamTop3 = yamTop3
        )
        Log.d("MainViewModel", "LOGGED -> $line")
    }

    fun getLogsCsvFile(): File {
        if (logsCsvFile == null) {
            val dir = AirquixApplication.appContext.getExternalFilesDir(null)
            logsCsvFile = File(dir, logsCsvFileName)
            if (!logsCsvFile!!.exists()) {
                logsCsvFile!!.writeText(
                    "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf,PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf,ACT,ACT_confidence," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf,VEHICLE_label,vehicle_conf\n"
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
                            "PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf,PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                            "ACT,act_conf_avg," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                            "YAMNET_top1_global_label,top1_global_conf," +
                            "YAMNET_top2_global_label,top2_global_conf," +
                            "YAMNET_top1_single_label,top1_single_conf," +
                            "YAMNET_top2_single_label,top2_single_conf\n"
                )
            }
        }
        return featureCsvFile!!
    }

    private fun saveFeatureVectorLine(line: String) {
        try {
            val file = getFeatureCsvFile()
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
            Log.d("MainViewModel", "FEATURE -> $line")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllLogs() {
        logList.clear()
        val logsFile = getLogsCsvFile()
        if (logsFile.exists()) {
            logsFile.delete()
        }
        logsFile.writeText(
            "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf,PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf,ACT,ACT_confidence," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf,VEHICLE_label,vehicle_conf\n"
        )
        val featureFile = getFeatureCsvFile()
        if (featureFile.exists()) {
            featureFile.delete()
        }
        featureFile.writeText(
            "timestamp," +
                    "PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf,PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                    "ACT,act_conf_avg," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                    "YAMNET_top1_global_label,top1_global_conf," +
                    "YAMNET_top2_global_label,top2_global_conf," +
                    "YAMNET_top1_single_label,top1_single_conf," +
                    "YAMNET_top2_single_label,top2_single_conf\n"
        )
    }

    // Aggregator (aggregiert Places-, Activity- und YamNet-Daten)
    inner class TwoMinuteAggregator {
        private val placesBuffer = mutableListOf<Pair<Pair<String, Float>, Pair<String, Float>>>()
        private val actBuffer = mutableListOf<DetectedActivityData>()
        private val yamBuffer = mutableListOf<List<LabelConfidence>>()
        private var counter = 0

        fun addData(
            places: Pair<Pair<String, Float>, Pair<String, Float>>,
            act: DetectedActivityData?,
            yamTop3: List<LabelConfidence>
        ) {
            placesBuffer.add(places)
            if (act != null) {
                actBuffer.add(act)
            }
            yamBuffer.add(yamTop3)
            counter++
            if (counter >= 120) {
                createFeatureVectorAndSave()
                placesBuffer.clear()
                actBuffer.clear()
                yamBuffer.clear()
                counter = 0
            }
        }

        private fun createFeatureVectorAndSave() {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))
            val placesTop1 = placesBuffer.firstOrNull()?.first?.first ?: "Unknown"
            val placesTop2 = placesBuffer.firstOrNull()?.second?.first ?: "Unknown"
            val actLabel = if (actBuffer.isNotEmpty()) {
                val freqMap = mutableMapOf<String, Int>()
                actBuffer.forEach { a ->
                    freqMap[a.activityType] = freqMap.getOrDefault(a.activityType, 0) + 1
                }
                freqMap.maxByOrNull { it.value }?.key ?: "unknown"
            } else "unknown"
            val actConfAvg = if (actBuffer.isNotEmpty()) actBuffer.sumOf { it.confidence.toDouble() }.toFloat() / actBuffer.size else 0f
            val (top3Labels, top3Confs) = calculateTop3Yamnet()
            val (globalTop2Labels, globalTop2Confs) = calculateGlobalTop2Yamnet()
            val (singleTop2Labels, singleTop2Confs) = calculateSingleTop2Yamnet()

            val csvLine = buildString {
                append(csvEscape(timestamp)).append(",")
                append(csvEscape(placesTop1)).append(",")
                append("%.2f".format(Locale.US, placesBuffer.firstOrNull()?.first?.second ?: 0f)).append(",")
                append(csvEscape(placesTop2)).append(",")
                append("%.2f".format(Locale.US, placesBuffer.firstOrNull()?.second?.second ?: 0f))
                // Weitere Felder für ACT und YamNet folgen hier...
            }
            saveFeatureVectorLine(csvLine)
        }

        private fun calculateTop3Yamnet(): Pair<List<String>, List<Float>> {
            val labelCount = mutableMapOf<String, Int>()
            val labelConfSum = mutableMapOf<String, Float>()
            yamBuffer.forEach { top3List ->
                top3List.forEach { labelConf ->
                    labelCount[labelConf.label] = labelCount.getOrDefault(labelConf.label, 0) + 1
                    labelConfSum[labelConf.label] = labelConfSum.getOrDefault(labelConf.label, 0f) + labelConf.confidence
                }
            }
            if (labelCount.isEmpty()) return Pair(emptyList(), emptyList())
            val labelAvgConf = labelConfSum.mapValues { (lbl, sum) -> sum / labelCount[lbl]!! }
            val sortedLabels = labelCount.keys.sortedWith(
                compareByDescending<String> { labelCount[it] ?: 0 }
                    .thenByDescending { labelAvgConf[it] ?: 0f }
            )
            val top3 = sortedLabels.take(3)
            val top3Confs = top3.map { labelAvgConf[it] ?: 0f }
            return Pair(top3, top3Confs)
        }

        private fun calculateGlobalTop2Yamnet(): Pair<List<String>, List<Float>> {
            val labelSum = mutableMapOf<String, Float>()
            yamBuffer.forEach { top3List ->
                top3List.forEach { labelConf ->
                    labelSum[labelConf.label] = labelSum.getOrDefault(labelConf.label, 0f) + labelConf.confidence
                }
            }
            if (labelSum.isEmpty()) return Pair(emptyList(), emptyList())
            val sorted = labelSum.entries.sortedByDescending { it.value }
            val top2 = sorted.take(2)
            val top2Labels = top2.map { it.key }
            val top2Confs = top2.map { it.value }
            return Pair(top2Labels, top2Confs)
        }

        private fun calculateSingleTop2Yamnet(): Pair<List<String>, List<Float>> {
            val labelMax = mutableMapOf<String, Float>()
            yamBuffer.forEach { top3List ->
                top3List.forEach { labelConf ->
                    val oldVal = labelMax[labelConf.label] ?: 0f
                    labelMax[labelConf.label] = max(oldVal, labelConf.confidence)
                }
            }
            if (labelMax.isEmpty()) return Pair(emptyList(), emptyList())
            val sorted = labelMax.entries.sortedByDescending { it.value }
            val top2 = sorted.take(2)
            val top2Labels = top2.map { it.key }
            val top2Confs = top2.map { it.value }
            return Pair(top2Labels, top2Confs)
        }
    }

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
