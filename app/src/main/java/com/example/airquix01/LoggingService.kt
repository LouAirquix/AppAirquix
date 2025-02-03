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
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.exp

// Datenklasse für die Top-5 Ergebnisse von Places365
data class PlacesResults(
    val top1: Pair<String, Float>,
    val top2: Pair<String, Float>,
    val top3: Pair<String, Float>,
    val top4: Pair<String, Float>,
    val top5: Pair<String, Float>
)

class LoggingService : LifecycleService() {

    // Service-spezifischer CoroutineScope (Default-Dispatcher)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var viewModel: MainViewModel

    // Activity Recognition
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityPendingIntent: PendingIntent? = null

    // Kamera – Verwende den ImageCapture-Use Case (JPEG)
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Aufnahmeintervall: 5 Sekunden
    private val captureIntervalMillis = 5000L

    // Places365 (AlexNet) Modell und Kategorien
    private var classificationModel: Module? = null
    private var classificationCategories: List<String> = emptyList()

    // IO-Mapping: Liste, die zu jedem Klassenindex (0 bis 364) angibt, ob indoor (1) oder outdoor (2)
    private var ioMapping: List<Int> = emptyList()

    // YamNet Audio-Klassifikation
    private var audioClassifier: org.tensorflow.lite.task.audio.classifier.AudioClassifier? = null
    private var audioRecord: android.media.AudioRecord? = null
    private var yamNetJob: Job? = null

    // Vehicle Audio-Klassifikation
    private var vehicleClassifier: org.tensorflow.lite.task.audio.classifier.AudioClassifier? = null
    private var vehicleAudioRecord: android.media.AudioRecord? = null
    private var vehicleJob: Job? = null

    // Allowed label indices für YamNet (wie bisher)
    private val allowedLabelIndices = setOf(
        106, 107, 110, 116, 122, 277, 278, 279, 283, 285, 288, 289, 295, 298, 300, 301, 302, 303, 304,
        305, 308, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 323, 324, 325, 326, 327,
        328, 329, 330, 331, 352, 354, 355, 357, 358, 359, 378, 380, 500, 501, 502, 503, 504, 508, 517, 519, 520
    )

    // Definiere die auszuschließenden Labels (bereinigt)
    private val excludedLabels = setOf("medina", "catacomb")

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as AirquixApplication
        viewModel = app.getMainViewModel()
        viewModel.isLogging.value = true

        // Lade das IO-Mapping aus der Datei IO_places365.txt
        ioMapping = loadIOFile("IO_places365.txt")

        // Lade Modell und Kategorien
        try {
            classificationModel = Module.load(assetFilePath("alexnet_places365_quantized.pt"))
            classificationCategories = loadCategories("categories_places365.txt")
            Log.d("LoggingService", "AlexNet model and categories loaded.")
        } catch (e: Exception) {
            Log.e("LoggingService", "Error loading Places365 model or categories", e)
        }

        startForegroundServiceNotification()
        startActivityRecognition()
        setupCamera() // Startet nach erfolgreicher Kamera-Bindung die periodische Bildaufnahme
        startYamNetClassification()
        startVehicleClassification()
        startPeriodicLogging()  // Logge alle 5 Sekunden
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.isLogging.value = false
        serviceScope.cancel()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // ---------------- Foreground Notification ----------------
    private fun startForegroundServiceNotification() {
        val channelId = "LoggingServiceChannel"
        val channelName = "Logging Service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Logging active")
            .setContentText("Capturing images and processing labels every 15 sec")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(123, notification)
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

    // ---------------- Kamera Setup: Preview & ImageCapture ----------------
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Dummy-Preview (wird benötigt, auch wenn nur Bilder aufgenommen werden)
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request ->
                val texture = SurfaceTexture(0)
                texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(texture)
                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {
                    surface.release()
                    texture.release()
                }
            }

            // ImageCapture-Use Case: Maximale Qualität und Zielauflösung
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(Size(1920, 1080))
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.d("LoggingService", "Camera set up with high quality ImageCapture.")
                startPeriodicImageCapture()
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

    // ---------------- Periodische Bildaufnahme (alle 5 Sekunden) ----------------
    private fun startPeriodicImageCapture() {
        serviceScope.launch {
            while (isActive) {
                delay(300)
                captureImage()
                delay(captureIntervalMillis)
            }
        }
    }

    /**
     * Nimmt ein Bild mit ImageCapture auf, speichert es als JPEG in den Cache und verarbeitet es.
     */
    private fun captureImage() {
        Thread.sleep(300)
        val photoFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("LoggingService", "Photo capture failed: ${exception.message}", exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri: Uri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    Log.d("LoggingService", "Photo captured: $savedUri")
                    processCapturedImage(photoFile)
                }
            }
        )
    }

    /**
     * Dekodiert das JPEG aus der Datei in ein Bitmap, korrigiert die Orientierung,
     * führt die Places365-Klassifikation (AlexNet) aus und speichert die Ergebnisse im ViewModel.
     */
    private fun processCapturedImage(photoFile: File) {
        var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        if (bitmap != null) {
            try {
                val exif = ExifInterface(photoFile.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                bitmap = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } catch (e: Exception) {
                Log.e("LoggingService", "Error correcting image orientation: ${e.message}")
            }
        }

        if (bitmap != null && classificationModel != null && classificationCategories.isNotEmpty()) {
            Log.d("LoggingService", "Image processed. Bitmap size: ${bitmap.width} x ${bitmap.height}")
            serviceScope.launch(Dispatchers.Default) {
                // Erhalte die Top 5 Ergebnisse von Places365, dabei werden "medina" und "catacomb" ausgeschlossen
                val results = classifyImageAlexNet(bitmap, classificationModel!!, classificationCategories)
                // Bestimme den Scene-Typ (indoor/outdoor) mithilfe des IO-Mappings
                val sceneType = determineSceneType(results)
                withContext(Dispatchers.Main) {
                    viewModel.currentPlacesTop1.value = results.top1.first
                    viewModel.currentPlacesTop1Confidence.value = results.top1.second
                    viewModel.currentPlacesTop2.value = results.top2.first
                    viewModel.currentPlacesTop2Confidence.value = results.top2.second
                    viewModel.currentPlacesTop3.value = results.top3.first
                    viewModel.currentPlacesTop3Confidence.value = results.top3.second
                    viewModel.currentPlacesTop4.value = results.top4.first
                    viewModel.currentPlacesTop4Confidence.value = results.top4.second
                    viewModel.currentPlacesTop5.value = results.top5.first
                    viewModel.currentPlacesTop5Confidence.value = results.top5.second
                    viewModel.currentSceneType.value = sceneType
                }
            }
        } else {
            Log.e("LoggingService", "Failed to decode captured image or model/categories not loaded.")
        }
        photoFile.delete()
    }

    /**
     * Hilfsmethode zum Rotieren eines Bitmaps.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Führt die AlexNet-Klassifikation für Places365 durch.
     * Skaliert das Bitmap auf 224x224, erstellt einen Tensor, führt Inferenz durch und ermittelt die Top 5.
     * Dabei werden Labels, die in excludedLabels enthalten sind, herausgefiltert.
     */
    private fun classifyImageAlexNet(
        bitmap: Bitmap,
        model: Module,
        categories: List<String>
    ): PlacesResults {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        val outputTensor = model.forward(IValue.from(inputTensor)).toTensor()
        val outputArray = outputTensor.dataAsFloatArray
        val probabilities = softmax(outputArray)

        // Erzeuge Indexed-Objekte, filtere aber zuerst die ausgeschlossenen Labels heraus.
        val allIndexed = probabilities.withIndex()
        val filteredIndexed = allIndexed.filter { indexed ->
            // Hole das zugehörige Rohlabel (z. B. "/a/catacomb")
            val rawLabel = categories.getOrElse(indexed.index) { "Unknown" }
            val cleaned = cleanLabel(rawLabel)
            // Ausschließen, wenn das Label in der Liste der auszuschließenden Labels steht
            cleaned !in excludedLabels
        }

        val top5List = filteredIndexed.sortedByDescending { it.value }.take(5)

        val top1 = top5List.getOrNull(0)
        val top2 = top5List.getOrNull(1)
        val top3 = top5List.getOrNull(2)
        val top4 = top5List.getOrNull(3)
        val top5 = top5List.getOrNull(4)

        fun cleanLabel(raw: String): String {
            return raw.replace(Regex("^/\\w/"), "")
                .replace(Regex("\\s+\\d+\$"), "")
                .replace('_', ' ')
                .trim()
        }

        val label1 = if (top1 != null) cleanLabel(categories.getOrElse(top1.index) { "Unknown" }) else "Unknown"
        val conf1 = top1?.value ?: 0f
        val label2 = if (top2 != null) cleanLabel(categories.getOrElse(top2.index) { "Unknown" }) else "Unknown"
        val conf2 = top2?.value ?: 0f
        val label3 = if (top3 != null) cleanLabel(categories.getOrElse(top3.index) { "Unknown" }) else "Unknown"
        val conf3 = top3?.value ?: 0f
        val label4 = if (top4 != null) cleanLabel(categories.getOrElse(top4.index) { "Unknown" }) else "Unknown"
        val conf4 = top4?.value ?: 0f
        val label5 = if (top5 != null) cleanLabel(categories.getOrElse(top5.index) { "Unknown" }) else "Unknown"
        val conf5 = top5?.value ?: 0f

        return PlacesResults(
            top1 = Pair(label1, conf1),
            top2 = Pair(label2, conf2),
            top3 = Pair(label3, conf3),
            top4 = Pair(label4, conf4),
            top5 = Pair(label5, conf5)
        )
    }

    /**
     * Berechnet Softmax für ein FloatArray.
     */
    private fun softmax(scores: FloatArray): FloatArray {
        val max = scores.maxOrNull() ?: 0f
        val expScores = scores.map { exp((it - max).toDouble()).toFloat() }
        val sumExp = expScores.sum()
        return expScores.map { it / sumExp }.toFloatArray()
    }

    // ---------------- Hilfsfunktionen zum Laden von Assets ----------------
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

    private fun loadCategories(filename: String): List<String> {
        val categoriesList = mutableListOf<String>()
        applicationContext.assets.open(filename).bufferedReader().useLines { lines ->
            lines.forEach { categoriesList.add(it) }
        }
        return categoriesList
    }

    /**
     * Liest die Datei IO_places365.txt aus den Assets und gibt eine Liste von Integer-Werten zurück,
     * die für jede Kategorie (entsprechend der Reihenfolge in categories_places365.txt) den Indoor/Outdoor-Typ angibt.
     * 1 = indoor, 2 = outdoor.
     */
    private fun loadIOFile(filename: String): List<Int> {
        val result = mutableListOf<Int>()
        applicationContext.assets.open(filename).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    val ioVal = parts[1].toIntOrNull()
                    if (ioVal != null) {
                        result.add(ioVal)
                    }
                }
            }
        }
        return result
    }

    /**
     * Bestimmt den Indoor/Outdoor-Typ anhand des Top-1-Ergebnisses und des IO-Mappings.
     *
     * Hier wird angenommen, dass das Top-1-Label (results.top1.first) in der gleichen
     * Reihenfolge wie in categories_places365.txt steht. Wir suchen den Index des Labels in der Liste.
     * Falls das Mapping vorhanden ist, geben wir "indoor" zurück, wenn der entsprechende Wert 1 ist,
     * oder "outdoor" bei 2. Andernfalls "unknown".
     */
    private fun determineSceneType(results: PlacesResults): String {
        if (ioMapping.isNotEmpty() && classificationCategories.isNotEmpty()) {
            val top1Label = results.top1.first
            val cleanedTop1 = cleanLabel(top1Label)
            val index = classificationCategories.indexOfFirst { cleanLabel(it) == cleanedTop1 }
            if (index in ioMapping.indices) {
                return if (ioMapping[index] == 1) "indoor" else "outdoor"
            }
        }
        return "unknown"
    }

    // Hilfsmethode zur Bereinigung von Labels (wie in classifyImageAlexNet verwendet)
    private fun cleanLabel(raw: String): String {
        return raw.replace(Regex("^/\\w/"), "")
            .replace(Regex("\\s+\\d+\$"), "")
            .replace('_', ' ')
            .trim()
    }

    // ---------------- YamNet Audio-Klassifikation ----------------
    private fun startYamNetClassification() {
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

    private fun stopYamNet() {
        yamNetJob?.cancel()
        yamNetJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioClassifier = null
        Log.d("LoggingService", "YamNet classification stopped.")
    }

    // ---------------- Vehicle Audio-Klassifikation ----------------
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
                delay(captureIntervalMillis)
                val now = System.currentTimeMillis()
                val timeStr = sdf.format(Date(now))
                val placesTop1 = viewModel.currentPlacesTop1.value ?: "Unknown"
                val placesTop1Conf = viewModel.currentPlacesTop1Confidence.value
                val placesTop2 = viewModel.currentPlacesTop2.value ?: "Unknown"
                val placesTop2Conf = viewModel.currentPlacesTop2Confidence.value
                val placesTop3 = viewModel.currentPlacesTop3.value ?: "Unknown"
                val placesTop3Conf = viewModel.currentPlacesTop3Confidence.value
                val placesTop4 = viewModel.currentPlacesTop4.value ?: "Unknown"
                val placesTop4Conf = viewModel.currentPlacesTop4Confidence.value
                val placesTop5 = viewModel.currentPlacesTop5.value ?: "Unknown"
                val placesTop5Conf = viewModel.currentPlacesTop5Confidence.value
                val sceneType = viewModel.currentSceneType.value ?: "Unknown"
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
                    placesTop3 = placesTop3,
                    placesTop3Conf = placesTop3Conf,
                    placesTop4 = placesTop4,
                    placesTop4Conf = placesTop4Conf,
                    placesTop5 = placesTop5,
                    placesTop5Conf = placesTop5Conf,
                    sceneType = sceneType,
                    act = act,
                    actConf = actConf,
                    yamTop3 = yamTop3,
                    veh = veh
                )
            }
        }
    }
}
