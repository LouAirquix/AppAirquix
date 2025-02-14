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

    // Scene Type (z. B. "indoor" oder "outdoor")
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
                // Header für Logs (unverändert, 29 Spalten)
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
                // Neuer Header: bisherige 29 Spalten + 3 neue: vehicle_image, speed_m_s, noise_dB
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
                            "vehicle_image,speed_m_s,noise_dB\n"
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
                    "vehicle_top1_agg," +
                    "vehicle_image,speed_m_s,noise_dB\n"
        )
    }

    /**
     * Diese Methode erstellt zwei Strings:
     * - displayLine: nur die ursprünglichen 26 Spalten (wie bisher, für die UI)
     * - csvLine: der vollständige, erweiterte Log (32 Spalten) – inklusive der neuen Spalten
     *
     * In der UI wird displayLine verwendet, während csvLine in die Feature CSV geschrieben wird.
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

        // csvLine – alle 32 Spalten (erweiterter Log)
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
            // Hier werden nun die aggregierten Werte aus dem OneMinuteAggregator eingetragen
            // (Diese Werte werden später aggregiert und in createFeatureVectorAndSave() ermittelt)
            append("%.2f".format(Locale.US, speed)).append(",")
            append("%.2f".format(Locale.US, pegel))
        }
        // Nur displayLine wird in der UI angezeigt
        logList.add(0, displayLine)
        try {
            FileWriter(getLogsCsvFile(), true).use { writer ->
                writer.appendLine(csvLine)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Übergabe an den Aggregator:
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
            vehicle = veh,
            vehicleImage = currentNewModelOutput.value.first,
            speed = speed,
            noise = pegel
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

    // -------------------------------------------
    // 1-Minuten-Aggregator für Feature-Vektoren
    // -------------------------------------------
    private data class PlacesData(
        val top1: Pair<String, Float>,
        val top2: Pair<String, Float>,
        val top3: Pair<String, Float>,
        val top4: Pair<String, Float>,
        val top5: Pair<String, Float>
    )

    private val aggregator = OneMinuteAggregator()

    inner class OneMinuteAggregator {
        private val placesBuffer = mutableListOf<PlacesData>()
        private val sceneTypes = mutableListOf<String>()
        private val ioValues = mutableListOf<Float>()
        private val activityBuffer = mutableListOf<Pair<String, Int>>()
        private val yamnetBuffer = mutableListOf<List<LabelConfidence>>()
        private val vehicleBuffer = mutableListOf<LabelConfidence?>()
        // Neue Buffer für die zusätzlichen Variablen:
        private val vehicleImageBuffer = mutableListOf<String>()
        private val speedBuffer = mutableListOf<Float>()
        private val noiseBuffer = mutableListOf<Float>()

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
            vehicle: LabelConfidence?,
            vehicleImage: String,
            speed: Float,
            noise: Float
        ) {
            placesBuffer.add(PlacesData(placesTop1, placesTop2, placesTop3, placesTop4, placesTop5))
            sceneTypes.add(sceneType)
            ioValues.add(ioValue)
            activityBuffer.add(activity)
            yamnetBuffer.add(yamTop3)
            vehicleBuffer.add(vehicle)
            // Zusätzliche Werte sammeln:
            vehicleImageBuffer.add(vehicleImage)
            speedBuffer.add(speed)
            noiseBuffer.add(noise)

            counter++
            if (counter >= 12) { // 12 Intervalle à 5 Sekunden = 1 Minute
                createFeatureVectorAndSave()
                placesBuffer.clear()
                sceneTypes.clear()
                ioValues.clear()
                activityBuffer.clear()
                yamnetBuffer.clear()
                vehicleBuffer.clear()
                vehicleImageBuffer.clear()
                speedBuffer.clear()
                noiseBuffer.clear()
                counter = 0
            }
        }

        private fun createFeatureVectorAndSave() {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            // Aggregation der Places-Labels
            val placeCountMap = mutableMapOf<String, Pair<Int, Float>>()
            for (pd in placesBuffer) {
                listOf(pd.top1, pd.top2, pd.top3, pd.top4, pd.top5).forEach { (label, conf) ->
                    val old = placeCountMap[label] ?: (0 to 0f)
                    placeCountMap[label] = Pair(old.first + 1, old.second + conf)
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

            // Häufigster Scene Type
            val sceneCountMap = sceneTypes.groupingBy { it }.eachCount()
            val sceneMajor = sceneCountMap.maxByOrNull { it.value }?.key ?: "unknown"

            // Durchschnittlicher IO-Wert
            val avgIO = if (ioValues.isNotEmpty()) ioValues.sum() / ioValues.size else 1.5f
            val sceneIO = when {
                abs(avgIO - 1.5f) < 0.2f -> "unknown"
                avgIO < 1.5f -> "indoor"
                else -> "outdoor"
            }

            // Aggregation der Aktivität
            val hasVehicle = activityBuffer.any { it.first.equals("In Vehicle", ignoreCase = true) }
            val finalActLabel: String
            val finalActConf: Float
            if (hasVehicle) {
                finalActLabel = "vehicle"
                val vehList = activityBuffer.filter { it.first.equals("In Vehicle", ignoreCase = true) }
                finalActConf = if (vehList.isNotEmpty()) vehList.map { it.second }.average().toFloat() else 0f
            } else {
                val actCountMap = mutableMapOf<String, Pair<Int, Int>>()
                for ((label, conf) in activityBuffer) {
                    val old = actCountMap[label] ?: (0 to 0)
                    actCountMap[label] = Pair(old.first + 1, old.second + conf)
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

            // Aggregation der YamNet-Audio-Daten
            val freqMap = mutableMapOf<String, Pair<Int, Float>>()
            yamnetBuffer.forEach { list ->
                list.forEach { (lbl, conf) ->
                    val old = freqMap[lbl] ?: (0 to 0f)
                    freqMap[lbl] = Pair(old.first + 1, old.second + conf)
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

            // Globale Top1-2 (Summe der Konfidenzen)
            val sumMap = mutableMapOf<String, Float>()
            yamnetBuffer.forEach { list ->
                list.forEach { (lbl, conf) ->
                    sumMap[lbl] = sumMap.getOrDefault(lbl, 0f) + conf
                }
            }
            val sortedSum = sumMap.entries.sortedByDescending { it.value }
            val globTop1 = sortedSum.getOrNull(0)?.let { it.key to it.value } ?: ("none" to 0f)
            val globTop2 = sortedSum.getOrNull(1)?.let { it.key to it.value } ?: ("none" to 0f)

            // Single Top1-2 (Maximaler Konfidenzwert pro Label)
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

            // Aggregation der Vehicle-Top1-Daten
            val vehicleAggLabel = if (finalActLabel.equals("vehicle", ignoreCase = true)) {
                val vehCountMap = mutableMapOf<String, Int>()
                for (v in vehicleBuffer) {
                    if (v != null) {
                        vehCountMap[v.label] = vehCountMap.getOrDefault(v.label, 0) + 1
                    }
                }
                if (vehCountMap.isNotEmpty()) vehCountMap.maxByOrNull { it.value }?.key ?: "None" else "None"
            } else {
                "None"
            }

            // Aggregation der zusätzlichen Variablen:
            val aggregatedSpeed = if (speedBuffer.isNotEmpty()) speedBuffer.sum() / speedBuffer.size else 0f
            val aggregatedNoise = if (noiseBuffer.isNotEmpty()) noiseBuffer.sum() / noiseBuffer.size else 0f

            val aggregatedVehicleImage = if (finalActLabel.equals("vehicle", ignoreCase = true)) {
                if (vehicleImageBuffer.isNotEmpty()) {
                    val imageCountMap = mutableMapOf<String, Int>()
                    for (image in vehicleImageBuffer) {
                        imageCountMap[image] = imageCountMap.getOrDefault(image, 0) + 1
                    }
                    imageCountMap.maxByOrNull { it.value }?.key ?: "None"
                } else {
                    "None"
                }
            } else {
                "None"
            }


            // Erstelle die CSV-Zeile für feature_vectors.csv
            val csvLine = buildString {
                append(csvEscape(timestamp)).append(",")
                final5.forEachIndexed { index, pair ->
                    append(csvEscape(pair.first)).append(",")
                    append("%.2f".format(Locale.US, pair.second))
                    if (index < 4) append(",") else append(",")
                }
                append(csvEscape(sceneMajor)).append(",")
                append(csvEscape(sceneIO)).append(",")
                append(csvEscape(finalActLabel)).append(",")
                append("%.2f".format(Locale.US, finalActConf)).append(",")
                append(csvEscape(freqTop1.first)).append(",")
                append("%.2f".format(Locale.US, freqTop1.second)).append(",")
                append(csvEscape(freqTop2.first)).append(",")
                append("%.2f".format(Locale.US, freqTop2.second)).append(",")
                append(csvEscape(freqTop3p.first)).append(",")
                append("%.2f".format(Locale.US, freqTop3p.second)).append(",")
                append(csvEscape(globTop1.first)).append(",")
                append("%.2f".format(Locale.US, globTop1.second)).append(",")
                append(csvEscape(globTop2.first)).append(",")
                append("%.2f".format(Locale.US, globTop2.second)).append(",")
                append(csvEscape(singleTop1.first)).append(",")
                append("%.2f".format(Locale.US, singleTop1.second)).append(",")
                append(csvEscape(singleTop2.first)).append(",")
                append("%.2f".format(Locale.US, singleTop2.second)).append(",")
                append(csvEscape(vehicleAggLabel)).append(",")
                // Hier werden nun die aggregierten Werte eingetragen:
                append(csvEscape(aggregatedVehicleImage)).append(",")
                append("%.2f".format(Locale.US, aggregatedSpeed)).append(",")
                append("%.2f".format(Locale.US, aggregatedNoise))
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
