package com.example.airquix01

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.DetectedActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel : ViewModel() {

    // Flag, ob Logging aktiv ist
    val isLogging = mutableStateOf(false)

    // Kamerabasiertes Environment
    val currentEnvironment = mutableStateOf<String?>(null)
    val currentEnvironmentConfidence = mutableStateOf(0f)

    // Aktivität
    val detectedActivity = mutableStateOf<DetectedActivityData?>(null)

    // YAMNet: Top-3 pro Sekunde
    val currentYamnetTop3 = mutableStateOf<List<LabelConfidence>>(emptyList())

    // Logs (nur für die UI)
    val logList = mutableStateListOf<String>()

    // CSV-Dateinamen
    private val logsCsvFileName = "all_in_one_logs.csv"
    private val featureCsvFileName = "feature_vectors.csv"
    private var logsCsvFile: File? = null
    private var featureCsvFile: File? = null

    // Aggregator, der alle 2 Minuten eine Zeile in feature_vectors.csv schreibt
    private val aggregator = TwoMinuteAggregator()

    // ----------------------------
    // Datenklassen
    // ----------------------------
    data class DetectedActivityData(val activityType: String, val confidence: Int)
    data class LabelConfidence(val label: String, val confidence: Float)

    // ----------------------------
    // CSV-Escaping (um Kommas zu erlauben)
    // ----------------------------
    private fun csvEscape(str: String?): String {
        if (str == null) return ""
        return if (str.contains(",") || str.contains("\"")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    // ----------------------------
    // TwoMinuteAggregator
    // ----------------------------
    inner class TwoMinuteAggregator {
        private val envBuffer = mutableListOf<Pair<String, Float>>()   // (env, envConf)
        private val actBuffer = mutableListOf<DetectedActivityData>()
        private val yamBuffer = mutableListOf<List<LabelConfidence>>()

        private var counter = 0

        fun addData(
            env: String,
            envConf: Float,
            act: DetectedActivityData?,
            yamTop3: List<LabelConfidence>
        ) {
            envBuffer.add(env to envConf)
            if (act != null) {
                actBuffer.add(act)
            }
            yamBuffer.add(yamTop3)
            counter++

            // Nach 120 Einträgen = 2 Minuten (bei 1Hz) erstellen wir eine Feature-Zeile
            if (counter >= 120) {
                createFeatureVectorAndSave()
                envBuffer.clear()
                actBuffer.clear()
                yamBuffer.clear()
                counter = 0
            }
        }

        /**
         * Schreibt eine Zeile in feature_vectors.csv
         */
        private fun createFeatureVectorAndSave() {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))

            // 1) Environment: häufigstes
            val envLabel = calculateMostFrequentEnv()
            // 2) Aktivität: priorisiert
            val actLabel = calculatePriorityActivity()
            // 3) Top-3 YAMNet
            val (top3Labels, top3Confs) = calculateTop3Yamnet()

            // CSV-Aufbau: time, envLabel, actLabel, top1, top1Conf, top2, top2Conf, top3, top3Conf
            val csvLine = buildString {
                append(csvEscape(timestamp)).append(",")
                append(csvEscape(envLabel)).append(",")
                append(csvEscape(actLabel)).append(",")
                for (i in 0..2) {
                    val lbl = top3Labels.getOrNull(i) ?: "none"
                    val conf = top3Confs.getOrNull(i) ?: 0f
                    append(csvEscape(lbl)).append(",")
                    append(csvEscape("%.2f".format(Locale.US, conf)))
                    if (i < 2) append(",")
                }
            }
            saveFeatureVectorLine(csvLine)
        }

        private fun calculateMostFrequentEnv(): String {
            if (envBuffer.isEmpty()) return "Unknown"
            val freqMap = mutableMapOf<String, Int>()
            envBuffer.forEach { (env, _) ->
                freqMap[env] = freqMap.getOrDefault(env, 0) + 1
            }
            return freqMap.maxByOrNull { it.value }?.key ?: "Unknown"
        }

        private fun calculatePriorityActivity(): String {
            if (actBuffer.isEmpty()) return "unknown"

            var hasVehicle = false
            var hasOnBike = false
            actBuffer.forEach { a ->
                if (a.activityType == "In Vehicle") hasVehicle = true
                if (a.activityType == "On Bicycle") hasOnBike = true
            }
            if (hasVehicle) return "vehicle"
            if (hasOnBike) return "on_bike"

            // Sonst unknown < still < on_foot < running
            val freqMap = mutableMapOf<String, Int>()
            actBuffer.forEach { a ->
                freqMap[a.activityType] = freqMap.getOrDefault(a.activityType, 0) + 1
            }
            val best = freqMap.maxByOrNull { it.value }?.key ?: "unknown"
            return when (best) {
                "Still" -> "still"
                "On Foot" -> "on_foot"
                "Running" -> "running"
                else -> "unknown"
            }
        }

        private fun calculateTop3Yamnet(): Pair<List<String>, List<Float>> {
            val labelCount = mutableMapOf<String, Int>()
            val labelConfSum = mutableMapOf<String, Float>()

            yamBuffer.forEach { top3List ->
                top3List.forEach { labelConf ->
                    val label = labelConf.label
                    labelCount[label] = labelCount.getOrDefault(label, 0) + 1
                    labelConfSum[label] = labelConfSum.getOrDefault(label, 0f) + labelConf.confidence
                }
            }
            if (labelCount.isEmpty()) return Pair(emptyList(), emptyList())

            val labelAvgConf = labelConfSum.mapValues { (lbl, sum) ->
                sum / labelCount[lbl]!!
            }
            val sortedLabels = labelCount.keys.sortedWith(
                compareByDescending<String> { labelCount[it] ?: 0 }
                    .thenByDescending { labelAvgConf[it] ?: 0f }
            )

            val top3 = sortedLabels.take(3)
            val top3Confs = top3.map { labelAvgConf[it] ?: 0f }
            return Pair(top3, top3Confs)
        }
    }

    // ----------------------------
    // Aktivität updaten
    // ----------------------------
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

    // ----------------------------
    // Sekündliches Logging
    // ----------------------------
    fun appendLog(
        timeStr: String,
        env: String,
        envConf: Float,
        act: String,
        actConf: Int,
        yamTop3: List<LabelConfidence>
    ) {
        // => time,env,env_conf,act,act_conf,top1,top1Conf,top2,top2Conf,top3,top3Conf
        val top1 = yamTop3.getOrNull(0)
        val top2 = yamTop3.getOrNull(1)
        val top3 = yamTop3.getOrNull(2)

        val line = buildString {
            append(csvEscape(timeStr)).append(",")
            append(csvEscape(env)).append(",")
            append("%.2f".format(Locale.US, envConf)).append(",")
            append(csvEscape(act)).append(",")
            append(actConf).append(",")

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
            append(top3Conf)
        }

        logList.add(line)

        // In Logs-CSV schreiben
        try {
            val file = getLogsCsvFile()
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Dem Aggregator hinzufügen -> alle 2 Min => Feature-Vektor
        aggregator.addData(env, envConf, detectedActivity.value, yamTop3)

        Log.d("MainViewModel", "LOGGED -> $line")
    }

    // ----------------------------
    // CSV-Dateien
    // ----------------------------
    fun getLogsCsvFile(): File {
        if (logsCsvFile == null) {
            val dir = AirquixApplication.appContext.getExternalFilesDir(null)
            logsCsvFile = File(dir, logsCsvFileName)
            if (!logsCsvFile!!.exists()) {
                logsCsvFile!!.writeText(
                    "timestamp,ENV,ENV_confidence,ACT,ACT_confidence," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf\n"
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
                    "timestamp,ENV_label,ACT_label," +
                            "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf\n"
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

    // ----------------------------
    // Clear Logs
    // ----------------------------
    fun clearAllLogs() {
        logList.clear()

        val logsFile = getLogsCsvFile()
        if (logsFile.exists()) {
            logsFile.delete()
        }
        logsFile.writeText(
            "timestamp,ENV,ENV_confidence,ACT,ACT_confidence," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf\n"
        )

        val featureFile = getFeatureCsvFile()
        if (featureFile.exists()) {
            featureFile.delete()
        }
        featureFile.writeText(
            "timestamp,ENV_label,ACT_label," +
                    "YAMNET_top1,top1_conf,YAMNET_top2,top2_conf,YAMNET_top3,top3_conf\n"
        )
    }
}
