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

    // Logging-Status
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

    // Scene Type (z. B. "indoor", "outdoor", "unknown")
    val currentSceneType = mutableStateOf("Unknown")

    // Aktivität
    data class DetectedActivityData(val activityType: String, val confidence: Int)
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YamNet: Top-3
    data class LabelConfidence(val label: String, val confidence: Float)
    val currentYamnetTop3 = mutableStateOf<List<LabelConfidence>>(emptyList())

    // Vehicle-Top1 (z. B. zur Anzeige in der UI)
    val currentVehicleTop1 = mutableStateOf<LabelConfidence?>(null)

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
                logsCsvFile!!.writeText(
                    "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                            "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                            "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                            "VEHICLE_label,vehicle_conf\n"
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
                            "vehicle_top1_agg\n"
                )
            }
        }
        return featureCsvFile!!
    }

    fun clearAllLogs() {
        logList.clear()
        val logsFile = getLogsCsvFile()
        if (logsFile.exists()) logsFile.delete()
        logsFile.writeText(
            "timestamp,PLACES_top1,places_top1_conf,PLACES_top2,places_top2_conf," +
                    "PLACES_top3,places_top3_conf,PLACES_top4,places_top4_conf," +
                    "PLACES_top5,places_top5_conf,SCENE_TYPE,ACT,ACT_confidence," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf," +
                    "VEHICLE_label,vehicle_conf\n"
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
                    "vehicle_top1_agg\n"
        )
    }

    private fun csvEscape(str: String?): String {
        if (str == null) return ""
        return if (str.contains(",") || str.contains("\"")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    /**
     * Diese Methode wird alle 5 Sekunden vom LoggingService aufgerufen,
     * um den aktuellen Zustand zu protokollieren und gleichzeitig die Daten
     * für den 2-Minuten-Aggregator zu sammeln.
     *
     * CSV-Spalten (in all_in_one_logs.csv):
     * 0: timestamp
     * 1: PLACES_top1, 2: places_top1_conf, 3: PLACES_top2, 4: places_top2_conf, …
     * 21: VEHICLE_label, 22: vehicle_conf
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
            append("%.2f".format(Locale.US, veh?.confidence ?: 0f))
        }

        logList.add(0, line)
        try {
            FileWriter(getLogsCsvFile(), true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Übergabe der Daten an den Aggregator, inkl. des Vehicle-Parameters.
        aggregator.addData(
            timeStr = timeStr,
            placesTop1 = Pair(placesTop1, placesTop1Conf),
            placesTop2 = Pair(placesTop2, placesTop2Conf),
            placesTop3 = Pair(placesTop3, placesTop3Conf),
            placesTop4 = Pair(placesTop4, placesTop4Conf),
            placesTop5 = Pair(placesTop5, placesTop5Conf),
            sceneType = sceneType,
            ioValue = when {
                sceneType.equals("indoor", ignoreCase = true) -> 1f
                sceneType.equals("outdoor", ignoreCase = true) -> 2f
                else -> 1.5f
            },
            activity = act to actConf,
            yamTop3 = yamTop3,
            vehicle = veh
        )
    }

    // -------------------------------------------
    // C) 2-Minuten-Aggregator für Feature-Vektoren
    // -------------------------------------------

    // Diese Datenklasse fasst die Places-Daten zusammen.
    private data class PlacesData(
        val top1: Pair<String, Float>,
        val top2: Pair<String, Float>,
        val top3: Pair<String, Float>,
        val top4: Pair<String, Float>,
        val top5: Pair<String, Float>
    )

    private val aggregator = TwoMinuteAggregator()

    inner class TwoMinuteAggregator {
        // Buffer für die gesammelten Einträge (alle 5 Sekunden)
        private val placesBuffer = mutableListOf<PlacesData>()
        private val sceneTypes = mutableListOf<String>()
        private val ioValues = mutableListOf<Float>()
        private val activityBuffer = mutableListOf<Pair<String, Int>>()
        private val yamnetBuffer = mutableListOf<List<LabelConfidence>>()
        // Neuer Buffer für Vehicle-Top1-Werte
        private val vehicleBuffer = mutableListOf<LabelConfidence?>()

        private var counter = 0

        fun addData(
            timeStr: String,
            placesTop1: Pair<String, Float>,
            placesTop2: Pair<String, Float>,
            placesTop3: Pair<String, Float>,
            placesTop4: Pair<String, Float>,
            placesTop5: Pair<String, Float>,
            sceneType: String,
            ioValue: Float,
            activity: Pair<String, Int>,
            yamTop3: List<LabelConfidence>,
            vehicle: LabelConfidence?
        ) {
            placesBuffer.add(PlacesData(placesTop1, placesTop2, placesTop3, placesTop4, placesTop5))
            sceneTypes.add(sceneType)
            ioValues.add(ioValue)
            activityBuffer.add(activity)
            yamnetBuffer.add(yamTop3)
            vehicleBuffer.add(vehicle)

            counter++
            // Sobald ca. 2 Minuten (24 Einträge à 5 Sekunden) gesammelt wurden:
            if (counter >= 24) {
                createFeatureVectorAndSave()
                placesBuffer.clear()
                sceneTypes.clear()
                ioValues.clear()
                activityBuffer.clear()
                yamnetBuffer.clear()
                vehicleBuffer.clear()
                counter = 0
            }
        }

        private fun createFeatureVectorAndSave() {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            // 1) Aggregation der Places-Labels (Top 5)
            val placeCountMap = mutableMapOf<String, Pair<Int, Float>>() // label -> (Anzahl, Summe der Konfidenzen)
            for (pd in placesBuffer) {
                listOf(pd.top1, pd.top2, pd.top3, pd.top4, pd.top5).forEach { (label, conf) ->
                    val oldVal = placeCountMap[label] ?: (0 to 0f)
                    placeCountMap[label] = Pair(oldVal.first + 1, oldVal.second + conf)
                }
            }
            val sortedPlaces = placeCountMap.entries.sortedWith(
                compareByDescending<Map.Entry<String, Pair<Int, Float>>> { it.value.first }
                    .thenByDescending { it.value.second / it.value.first }
            )
            val top5 = sortedPlaces.take(5)
            val final5 = (0 until 5).map { i ->
                if (i < top5.size) {
                    val (lbl, pair) = top5[i]
                    val avgConf = if (pair.first > 0) pair.second / pair.first else 0f
                    lbl to avgConf
                } else {
                    "none" to 0f
                }
            }

            // 2) Häufigster Scene Type
            val sceneCountMap = sceneTypes.groupingBy { it }.eachCount()
            val sceneMajor = sceneCountMap.maxByOrNull { it.value }?.key ?: "unknown"

            // 3) Aggregation des IO-Mappings
            val avgIO = if (ioValues.isNotEmpty()) ioValues.sum() / ioValues.size else 1.5f
            val sceneIO = when {
                abs(avgIO - 1.5f) < 0.2f -> "unknown"
                avgIO < 1.5f -> "indoor"
                else -> "outdoor"
            }

            // 4) Aggregation der Aktivität
            val hasVehicle = activityBuffer.any { it.first.equals("In Vehicle", ignoreCase = true) }
            val finalActLabel: String
            val finalActConf: Float
            if (hasVehicle) {
                finalActLabel = "vehicle"
                val vehList = activityBuffer.filter { it.first.equals("In Vehicle", ignoreCase = true) }
                finalActConf = if (vehList.isNotEmpty()) vehList.map { it.second }.average().toFloat() else 0f
            } else {
                val actCountMap = mutableMapOf<String, Pair<Int, Int>>() // label -> (Anzahl, Summe der Konfidenzen)
                for ((label, conf) in activityBuffer) {
                    val oldVal = actCountMap[label] ?: (0 to 0)
                    actCountMap[label] = Pair(oldVal.first + 1, oldVal.second + conf)
                }
                val bestAct = actCountMap.maxByOrNull { it.value.first }
                if (bestAct != null) {
                    finalActLabel = bestAct.key
                    finalActConf = if (bestAct.value.first > 0) bestAct.value.second.toFloat() / bestAct.value.first else 0f
                } else {
                    finalActLabel = "unknown"
                    finalActConf = 0f
                }
            }

            // 5) Aggregation der YamNet-Audio-Daten
            // 5a) Häufigste (Top1-3)
            val freqMap = mutableMapOf<String, Pair<Int, Float>>()
            yamnetBuffer.forEach { list ->
                list.forEach { (lbl, conf) ->
                    val oldVal = freqMap[lbl] ?: (0 to 0f)
                    freqMap[lbl] = Pair(oldVal.first + 1, oldVal.second + conf)
                }
            }
            val sortedFreq = freqMap.entries.sortedWith(
                compareByDescending<Map.Entry<String, Pair<Int, Float>>> { it.value.first }
                    .thenByDescending { it.value.second / it.value.first }
            )
            val freqTop3 = sortedFreq.take(3)
            fun labelAvgConf(e: Map.Entry<String, Pair<Int, Float>>): Pair<String, Float> {
                return e.key to (if (e.value.first > 0) e.value.second / e.value.first else 0f)
            }
            val freqTop1 = freqTop3.getOrNull(0)?.let { labelAvgConf(it) } ?: ("none" to 0f)
            val freqTop2 = freqTop3.getOrNull(1)?.let { labelAvgConf(it) } ?: ("none" to 0f)
            val freqTop3p = freqTop3.getOrNull(2)?.let { labelAvgConf(it) } ?: ("none" to 0f)

            // 5b) Globale Top1-2 (Summe der Konfidenzen)
            val sumMap = mutableMapOf<String, Float>()
            yamnetBuffer.forEach { list ->
                list.forEach { (lbl, conf) ->
                    sumMap[lbl] = sumMap.getOrDefault(lbl, 0f) + conf
                }
            }
            val sortedSum = sumMap.entries.sortedByDescending { it.value }
            val globTop1 = sortedSum.getOrNull(0)?.let { it.key to it.value } ?: ("none" to 0f)
            val globTop2 = sortedSum.getOrNull(1)?.let { it.key to it.value } ?: ("none" to 0f)

            // 5c) Single Top1-2 (maximaler Konfidenzwert pro Label)
            val maxMap = mutableMapOf<String, Float>()
            yamnetBuffer.forEach { list ->
                list.forEach { (lbl, conf) ->
                    val old = maxMap[lbl] ?: 0f
                    if (conf > old) {
                        maxMap[lbl] = conf
                    }
                }
            }
            val sortedMax = maxMap.entries.sortedByDescending { it.value }
            val singleTop1 = sortedMax.getOrNull(0)?.let { it.key to it.value } ?: ("none" to 0f)
            val singleTop2 = sortedMax.getOrNull(1)?.let { it.key to it.value } ?: ("none" to 0f)

            // 6) Aggregation der Vehicle-Top1-Daten:
            // Falls in diesem Zeitraum mindestens einmal das Activity-Label "In Vehicle" aufgetreten ist,
            // ermitteln wir das am häufigsten vorkommende Vehicle-Label aus dem vehicleBuffer.
            // Andernfalls setzen wir "None".
            val vehicleAggLabel = if (finalActLabel.equals("vehicle", ignoreCase = true)) {
                val vehCountMap = mutableMapOf<String, Int>()
                for (v in vehicleBuffer) {
                    if (v != null) {
                        vehCountMap[v.label] = vehCountMap.getOrDefault(v.label, 0) + 1
                    }
                }
                if (vehCountMap.isNotEmpty()) {
                    vehCountMap.maxByOrNull { it.value }?.key ?: "None"
                } else {
                    "None"
                }
            } else {
                "None"
            }

            // Erstelle die CSV-Zeile für feature_vectors.csv
            val csvLine = buildString {
                append(csvEscape(timestamp)).append(",")
                // Places Top1-5
                final5.forEachIndexed { index, pair ->
                    append(csvEscape(pair.first)).append(",")
                    append("%.2f".format(Locale.US, pair.second))
                    if (index < 4) append(",") else append(",")
                }
                append(csvEscape(sceneMajor)).append(",")
                append(csvEscape(sceneIO)).append(",")
                append(csvEscape(finalActLabel)).append(",")
                append("%.2f".format(Locale.US, finalActConf)).append(",")
                // YamNet – Häufigste Top1-3
                append(csvEscape(freqTop1.first)).append(",")
                append("%.2f".format(Locale.US, freqTop1.second)).append(",")
                append(csvEscape(freqTop2.first)).append(",")
                append("%.2f".format(Locale.US, freqTop2.second)).append(",")
                append(csvEscape(freqTop3p.first)).append(",")
                append("%.2f".format(Locale.US, freqTop3p.second)).append(",")
                // YamNet – Globale Top1-2
                append(csvEscape(globTop1.first)).append(",")
                append("%.2f".format(Locale.US, globTop1.second)).append(",")
                append(csvEscape(globTop2.first)).append(",")
                append("%.2f".format(Locale.US, globTop2.second)).append(",")
                // YamNet – Single Top1-2
                append(csvEscape(singleTop1.first)).append(",")
                append("%.2f".format(Locale.US, singleTop1.second)).append(",")
                append(csvEscape(singleTop2.first)).append(",")
                append("%.2f".format(Locale.US, singleTop2.second)).append(",")
                // Neu: Vehicle Aggregation (zusätzliche Spalte)
                append(csvEscape(vehicleAggLabel))
            }

            try {
                FileWriter(getFeatureCsvFile(), true).use { writer ->
                    writer.appendLine(csvLine)
                }
                Log.d("MainViewModel", "FEATURE -> $csvLine")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // -------------------------------------------
    // D) Aktualisierung der Aktivität im ViewModel
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
