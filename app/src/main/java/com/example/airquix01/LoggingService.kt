package com.example.airquix01

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.AudioRecord
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
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class LoggingService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ViewModel
    private lateinit var viewModel: MainViewModel

    // Activity Recognition
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityPendingIntent: PendingIntent? = null

    // Kamera
    private var cameraProvider: ProcessCameraProvider? = null

    // YamNet
    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var yamNetJob: Job? = null

    // Erlaubte Label-Indizes
    private val allowedLabelIndices = setOf(
        303,304,305,308,310,311,312,313,314,317,318,319,320,321,323,324,325,326,327,328,335,
        344,345,346,347,348,349,350,351,352,353,354,355,
        500,501,502,503,504
    )

    override fun onCreate() {
        super.onCreate()

        val app = applicationContext as AirquixApplication
        viewModel = app.getMainViewModel()
        viewModel.isLogging.value = true

        startForegroundServiceNotification()
        startActivityRecognition()
        setupCamera()
        startYamnetClassification()
        startPeriodicLogging()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopActivityRecognition()
        stopCamera()
        stopYamnet()
        viewModel.isLogging.value = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    // --------------------- FOREGROUND NOTIFICATION ---------------------
    private fun startForegroundServiceNotification() {
        val channelId = "LoggingServiceChannel"
        val channelName = "Logging Service"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Logging active")
            .setContentText("Collecting camera environment, activity, and audio data…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(111, notification)
    }

    // --------------------- ACTIVITY RECOGNITION ---------------------
    private fun startActivityRecognition() {
        if (!hasActivityRecognitionPermission()) {
            Log.e("LoggingService", "Missing Activity Recognition permission.")
            stopSelf()
            return
        }
        activityRecognitionClient = ActivityRecognition.getClient(this)

        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pending = PendingIntent.getBroadcast(this, 0, intent, flags)
        activityPendingIntent = pending

        try {
            activityRecognitionClient.requestActivityUpdates(5000, pending)
            Log.d("LoggingService", "Activity Recognition gestartet.")
        } catch (e: SecurityException) {
            Log.e("LoggingService", "Aktivitätserkennung fehlgeschlagen: ${e.message}")
            stopSelf()
        }
    }

    private fun stopActivityRecognition() {
        if (hasActivityRecognitionPermission()) {
            activityPendingIntent?.let {
                try {
                    activityRecognitionClient.removeActivityUpdates(it)
                    Log.d("LoggingService", "Activity Recognition gestoppt.")
                } catch (e: SecurityException) {
                    Log.e("LoggingService", "Fehler beim Entfernen der Activity Updates: ${e.message}")
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

    // --------------------- KAMERA ---------------------
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                ImageLabelAnalyzer { labels ->
                    val (env, conf) = EnvironmentDetector.detectEnvironmentWithConfidence(labels)
                    viewModel.currentEnvironment.value = env
                    viewModel.currentEnvironmentConfidence.value = conf
                }
            )

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
                Log.d("LoggingService", "Kamera erfolgreich eingerichtet.")
            } catch (exc: Exception) {
                exc.printStackTrace()
                Log.e("LoggingService", "Fehler beim Einrichten der Kamera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        Log.d("LoggingService", "Kamera gestoppt.")
    }

    // --------------------- YAMNET AUDIO-KLASSIFIKATION ---------------------
    private fun startYamnetClassification() {
        try {
            val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"
            audioClassifier = AudioClassifier.createFromFile(this, modelPath)

            audioRecord = audioClassifier?.createAudioRecord()
            audioRecord?.startRecording()

            val tensor = audioClassifier?.createInputTensorAudio()
            if (tensor == null || audioRecord == null) {
                Log.e("LoggingService", "YamNet classifier oder audioRecord ist null!")
                return
            }

            yamNetJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    tensor.load(audioRecord!!)
                    val outputs = audioClassifier!!.classify(tensor)

                    // Labels filtern
                    val filtered = outputs[0].categories.filter { c ->
                        allowedLabelIndices.contains(c.index)
                    }

                    // Top 3
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
            Log.d("LoggingService", "YamNet Klassifikation gestartet.")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("LoggingService", "Fehler beim Starten der YamNet Klassifikation: ${e.message}")
        }
    }

    private fun stopYamnet() {
        yamNetJob?.cancel()
        yamNetJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioClassifier = null
        Log.d("LoggingService", "YamNet Klassifikation gestoppt.")
    }

    // --------------------- PERIODISCHES LOGGING ---------------------
    private fun startPeriodicLogging() {
        serviceScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            while (isActive) {
                delay(1000) // jede Sekunde

                val now = System.currentTimeMillis()
                val timeStr = sdf.format(Date(now))

                val env = viewModel.currentEnvironment.value ?: "Unknown"
                val envConf = viewModel.currentEnvironmentConfidence.value
                val act = viewModel.detectedActivity.value?.activityType ?: "Unknown"
                val actConf = viewModel.detectedActivity.value?.confidence ?: 0
                val yamTop3 = viewModel.currentYamnetTop3.value

                viewModel.appendLog(
                    timeStr = timeStr,
                    env = env,
                    envConf = envConf,
                    act = act,
                    actConf = actConf,
                    yamTop3 = yamTop3
                )
            }
        }
    }
}
