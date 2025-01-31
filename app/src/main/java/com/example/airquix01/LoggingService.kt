// Datei: app/src/main/java/com/example/airquix01/LoggingService.kt
package com.example.airquix01

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import kotlinx.coroutines.*
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.exp

class LoggingService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var viewModel: MainViewModel

    // Activity Recognition
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityPendingIntent: PendingIntent? = null

    // Kamera
    private var cameraProvider: ProcessCameraProvider? = null

    // YamNet Audio-Klassifikation (unverändert)
    private var audioClassifier: org.tensorflow.lite.task.audio.classifier.AudioClassifier? = null
    private var audioRecord: android.media.AudioRecord? = null
    private var yamNetJob: Job? = null

    // Vehicle Audio-Klassifikation (optional, unverändert)
    private var vehicleClassifier: org.tensorflow.lite.task.audio.classifier.AudioClassifier? = null
    private var vehicleAudioRecord: android.media.AudioRecord? = null
    private var vehicleJob: Job? = null

    // Allowed label indices für YamNet
    private val allowedLabelIndices = setOf(
        106, 107, 110, 116, 122, 277, 278, 279, 283, 285, 288, 289, 295, 298, 300, 301, 302, 303, 304,
        305, 308, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 323, 324, 325, 326, 327,
        328, 329, 330, 331, 333, 334, 335, 338, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352,
        354, 355, 357, 358, 359, 378, 380, 500, 501, 502, 503, 504, 508, 517, 519, 520
    )

    // Felder für das AlexNet Modell (Places365)
    private var classificationModel: Module? = null
    private var classificationCategories: List<String> = emptyList()

    // Throttling: Nur alle 5 Sekunden ein Bild verarbeiten
    private var lastPlacesClassificationTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as AirquixApplication
        viewModel = app.getMainViewModel()
        viewModel.isLogging.value = true

        // Lade das AlexNet Modell und die Kategorienliste aus den Assets
        try {
            classificationModel = Module.load(assetFilePath("alexnet_places365_quantized.pt"))
            classificationCategories = loadCategories("categories_places365.txt")
            Log.d("LoggingService", "AlexNet model and categories loaded.")
        } catch (e: Exception) {
            Log.e("LoggingService", "Error loading AlexNet model or categories", e)
        }

        startForegroundServiceNotification()
        startActivityRecognition()
        setupCamera() // Hier wird der neue Analyzer genutzt
        startYamnetClassification()
        startVehicleClassification()
        startPeriodicLogging() // Loggen erfolgt jetzt alle 5 Sekunden
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopActivityRecognition()
        stopCamera()
        stopYamnet()
        stopVehicleClassification()
        viewModel.isLogging.value = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    // ---------------- Foreground Notification ----------------
    private fun startForegroundServiceNotification() {
        val channelId = "LoggingServiceChannel"
        val channelName = "Logging Service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(channel)
            }
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Logging active")
            .setContentText("Collecting camera, activity and audio data…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(111, notification)
    }

    // ---------------- Activity Recognition ----------------
    private fun startActivityRecognition() {
        if (!hasActivityRecognitionPermission()) {
            Log.e("LoggingService", "Missing Activity Recognition permission.")
            stopSelf()
            return
        }
        activityRecognitionClient = ActivityRecognition.getClient(this)
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getBroadcast(this, 0, intent, flags)
        activityPendingIntent = pending
        try {
            activityRecognitionClient.requestActivityUpdates(5000, pending)
            Log.d("LoggingService", "Activity Recognition started.")
        } catch (e: SecurityException) {
            Log.e("LoggingService", "Activity Recognition failed: ${e.message}")
            stopSelf()
        }
    }

    private fun stopActivityRecognition() {
        if (hasActivityRecognitionPermission()) {
            activityPendingIntent?.let {
                try {
                    activityRecognitionClient.removeActivityUpdates(it)
                    Log.d("LoggingService", "Activity Recognition stopped.")
                } catch (e: SecurityException) {
                    Log.e("LoggingService", "Error removing activity updates: ${e.message}")
                }
            }
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------- Kamera Setup mit neuem AlexNet Analyzer ----------------
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Verwende einen Analyzer, der die AlexNet-Klassifikation ausführt
            analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                processImageProxyWithAlexNet(imageProxy)
            }

            preview.setSurfaceProvider { request ->
                val texture = SurfaceTexture(0)
                texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(texture)
                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {
                    surface.release()
                    texture.release()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analyzer
                )
                Log.d("LoggingService", "Camera set up with AlexNet analyzer.")
            } catch (exc: Exception) {
                Log.e("LoggingService", "Error setting up camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        Log.d("LoggingService", "Camera stopped.")
    }

    // ---------------- Verarbeitung des Kamera-Frames mittels AlexNet (mit Throttling) ----------------
    private fun processImageProxyWithAlexNet(imageProxy: ImageProxy) {
        // Throttling: Nur alle 5 Sekunden ein Bild verarbeiten
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlacesClassificationTime < 5000) {
            imageProxy.close()
            return
        }
        lastPlacesClassificationTime = currentTime

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        // Konvertiere ImageProxy in ein Bitmap (verwende eine Hilfsfunktion aus ImageUtils)
        val bitmap = ImageUtils.imageToBitmap(imageProxy)
        imageProxy.close()
        if (bitmap != null && classificationModel != null && classificationCategories.isNotEmpty()) {
            serviceScope.launch(Dispatchers.Default) {
                // Klassifiziere das Bild – erhalte Top-1 und Top-2 Ergebnisse
                val (top1, top2) = classifyImageAlexNet(bitmap, classificationModel!!, classificationCategories)
                withContext(Dispatchers.Main) {
                    viewModel.currentPlacesTop1.value = top1.first
                    viewModel.currentPlacesTop1Confidence.value = top1.second
                    viewModel.currentPlacesTop2.value = top2.first
                    viewModel.currentPlacesTop2Confidence.value = top2.second
                }
            }
        }
    }

    // ---------------- AlexNet Bildklassifikation mit Label-Bereinigung ----------------
    private fun classifyImageAlexNet(
        bitmap: Bitmap,
        model: Module,
        categories: List<String>
    ): Pair<Pair<String, Float>, Pair<String, Float>> {
        // Skalierung auf 224x224, wie vom Modell erwartet
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        val outputTensor = model.forward(IValue.from(inputTensor)).toTensor()
        val outputArray = outputTensor.dataAsFloatArray
        val probabilities = softmax(outputArray)
        val top2 = probabilities.withIndex().sortedByDescending { it.value }.take(2)
        val top1 = top2.getOrNull(0)
        val top2Result = top2.getOrNull(1)

        // Neue cleanLabel-Funktion: Entfernt jedes führende "/<Buchstabe>/",
        // entfernt den am Ende stehenden Zahlenteil und ersetzt Unterstriche durch Leerzeichen.
        fun cleanLabel(raw: String): String {
            return raw.replace(Regex("^/\\w/"), "")   // entfernt z.B. "/c/" oder "/b/"
                .replace(Regex("\\s+\\d+\$"), "") // entfernt am Ende stehende Zahlen
                .replace('_', ' ')              // ersetzt Unterstriche durch Leerzeichen
                .trim()
        }

        val label1 = if (top1 != null)
            cleanLabel(categories.getOrElse(top1.index) { "Unknown" })
        else "Unknown"
        val conf1 = top1?.value ?: 0f
        val label2 = if (top2Result != null)
            cleanLabel(categories.getOrElse(top2Result.index) { "Unknown" })
        else "Unknown"
        val conf2 = top2Result?.value ?: 0f
        return Pair(Pair(label1, conf1), Pair(label2, conf2))
    }

    private fun softmax(scores: FloatArray): FloatArray {
        val max = scores.maxOrNull() ?: 0f
        val expScores = scores.map { exp((it - max).toDouble()).toFloat() }
        val sumExp = expScores.sum()
        return expScores.map { it / sumExp }.toFloatArray()
    }

    // ---------------- Hilfsfunktionen zum Laden von Assets ----------------
    private fun loadCategories(filename: String): List<String> {
        val categoriesList = mutableListOf<String>()
        assets.open(filename).bufferedReader().useLines { lines ->
            lines.forEach { categoriesList.add(it) }
        }
        return categoriesList
    }

    private fun assetFilePath(assetName: String): String {
        val file = File(applicationContext.filesDir, assetName)
        if (!file.exists() || file.length() == 0L) {
            applicationContext.assets.open(assetName).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    // ---------------- YamNet und Vehicle Audio-Klassifikation (unverändert) ----------------
    private fun startYamnetClassification() {
        try {
            val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"
            audioClassifier = org.tensorflow.lite.task.audio.classifier.AudioClassifier.createFromFile(this, modelPath)
            audioRecord = audioClassifier?.createAudioRecord()
            audioRecord?.startRecording()
            val tensor = audioClassifier?.createInputTensorAudio()
            if (tensor == null || audioRecord == null) {
                Log.e("LoggingService", "YamNet classifier or audioRecord is null!")
                return
            }
            yamNetJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    tensor.load(audioRecord!!)
                    val outputs = audioClassifier!!.classify(tensor)
                    val filtered = outputs[0].categories.filter { c ->
                        allowedLabelIndices.contains(c.index)
                    }
                    val top3 = filtered.sortedByDescending { it.score }.take(3)
                    val top3LabelConf = top3.map { category ->
                        MainViewModel.LabelConfidence(
                            label = category.label,
                            confidence = category.score
                        )
                    }
                    viewModel.currentYamnetTop3.value = top3LabelConf
                    delay(500)
                }
            }
            Log.d("LoggingService", "YamNet classification started.")
        } catch (e: Exception) {
            Log.e("LoggingService", "Error starting YamNet classification: ${e.message}")
        }
    }

    private fun stopYamnet() {
        yamNetJob?.cancel()
        yamNetJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioClassifier = null
        Log.d("LoggingService", "YamNet classification stopped.")
    }

    private fun startVehicleClassification() {
        try {
            val vehicleModelPath = "vehicle_sounds.tflite"
            vehicleClassifier = org.tensorflow.lite.task.audio.classifier.AudioClassifier.createFromFile(this, vehicleModelPath)
            vehicleAudioRecord = vehicleClassifier?.createAudioRecord()
            vehicleAudioRecord?.startRecording()
            val tensor = vehicleClassifier?.createInputTensorAudio()
            if (tensor == null || vehicleAudioRecord == null) {
                Log.e("LoggingService", "Vehicle classifier or vehicleAudioRecord is null!")
                return
            }
            vehicleJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    tensor.load(vehicleAudioRecord!!)
                    val outputs = vehicleClassifier!!.classify(tensor)
                    val categories = outputs[0].categories
                    val top1 = categories.maxByOrNull { it.score }
                    if (top1 != null && top1.score > 0.5f) {
                        viewModel.currentVehicleTop1.value = MainViewModel.LabelConfidence(top1.label, top1.score)
                    } else {
                        viewModel.currentVehicleTop1.value = MainViewModel.LabelConfidence("none", 0f)
                    }
                    delay(500)
                }
            }
            Log.d("LoggingService", "Vehicle classification started.")
        } catch (e: Exception) {
            Log.e("LoggingService", "Error starting Vehicle classification: ${e.message}")
        }
    }

    private fun stopVehicleClassification() {
        vehicleJob?.cancel()
        vehicleJob = null
        vehicleAudioRecord?.stop()
        vehicleAudioRecord?.release()
        vehicleAudioRecord = null
        vehicleClassifier = null
        Log.d("LoggingService", "Vehicle classification stopped.")
    }

    // ---------------- Periodisches Logging (alle 5 Sekunden) ----------------
    private fun startPeriodicLogging() {
        serviceScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            while (isActive) {
                delay(5000) // Loggen alle 5 Sekunden
                val now = System.currentTimeMillis()
                val timeStr = sdf.format(Date(now))
                val placesTop1 = viewModel.currentPlacesTop1.value ?: "Unknown"
                val placesTop1Conf = viewModel.currentPlacesTop1Confidence.value
                val placesTop2 = viewModel.currentPlacesTop2.value ?: "Unknown"
                val placesTop2Conf = viewModel.currentPlacesTop2Confidence.value
                val act = viewModel.detectedActivity.value?.activityType ?: "Unknown"
                val actConf = viewModel.detectedActivity.value?.confidence ?: 0
                val yamTop3 = viewModel.currentYamnetTop3.value
                val veh = viewModel.currentVehicleTop1.value
                viewModel.appendLog(
                    timeStr = timeStr,
                    placesTop1 = placesTop1,
                    placesTop1Conf = placesTop1Conf,
                    placesTop2 = placesTop2,
                    placesTop2Conf = placesTop2Conf,
                    act = act,
                    actConf = actConf,
                    yamTop3 = yamTop3,
                    veh = veh
                )
            }
        }
    }
}
