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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class LoggingService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Referenz auf unser App-ViewModel
    private lateinit var viewModel: MainViewModel

    // ActivityRecognitionClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityPendingIntent: PendingIntent? = null

    // Kamera-Provider
    private var cameraProvider: ProcessCameraProvider? = null

    // YamNet
    private var audioClassifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var yamNetJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Hole das ViewModel
        val app = applicationContext as AirquixApplication
        viewModel = app.getMainViewModel()

        // Setze Flag isLogging auf true
        viewModel.isLogging.value = true

        // Starte Notification für Foreground Service
        startForegroundServiceNotification()

        // 1) Starte Activity Recognition (BroadcastReceiver ruft dann viewModel.updateDetectedActivity auf)
        startActivityRecognition()

        // 2) Richte die Kamera ein
        setupCamera()

        // 3) Starte YamNet-Audio-Klassifikation
        startYamnetClassification()

        // 4) Starte periodisches Logging in die CSV
        startPeriodicLogging()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Aufräumen
        serviceScope.cancel()
        stopActivityRecognition()
        stopCamera()
        stopYamnet()
        // Flag
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

        // Damit der Nutzer die Activity über die Notification öffnen kann
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
        // Überprüfe Berechtigungen
        if (!hasActivityRecognitionPermission()) {
            Log.e("LoggingService", "Missing Activity Recognition permission.")
            stopSelf()
            return
        }

        activityRecognitionClient = ActivityRecognition.getClient(this)

        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pending = PendingIntent.getBroadcast(this, 0, intent, flags)
        activityPendingIntent = pending

        try {
            // Update alle 5 Sekunden (5000ms) – je nach Bedarf
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
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Für Android 9 und darunter verwenden wir ACCESS_FINE_LOCATION oder ACCESS_COARSE_LOCATION
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --------------------- KAMERA (Umgebungserkennung) ---------------------
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            // ImageAnalysis => MLKit ImageLabeling
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analyzer.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                ImageLabelAnalyzer { labels ->
                    // Wir suchen uns das Environment + Confidence
                    val (env, conf) = EnvironmentDetector.detectEnvironmentWithConfidence(labels)
                    viewModel.currentEnvironment.value = env
                    viewModel.currentEnvironmentConfidence.value = conf
                }
            )

            preview.setSurfaceProvider { request ->
                // Dummy-Surface, damit CameraX "läuft", obwohl wir kein Preview anzeigen
                val texture = SurfaceTexture(0)
                texture.setDefaultBufferSize(
                    request.resolution.width,
                    request.resolution.height
                )
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
            // Modell liegt in assets: lite-model_yamnet_classification_tflite_1.tflite
            val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"
            audioClassifier = AudioClassifier.createFromFile(this, modelPath)

            // AudioRecord anlegen
            audioRecord = audioClassifier?.createAudioRecord()
            audioRecord?.startRecording()

            val tensor = audioClassifier?.createInputTensorAudio()
            if (tensor == null || audioRecord == null) {
                Log.e("LoggingService", "YamNet classifier oder audioRecord ist null!")
                return
            }

            // Alle 500ms Audio vom Mikrofon holen & klassifizieren
            yamNetJob = serviceScope.launch(Dispatchers.Default) {
                while (isActive) {
                    tensor.load(audioRecord!!)
                    val outputs = audioClassifier!!.classify(tensor)
                    val filtered = outputs[0].categories.filter {
                        it.score > 0.3f
                    }.sortedByDescending { it.score }

                    if (filtered.isNotEmpty()) {
                        // Nimm das Top-Label
                        val top = filtered.first()
                        viewModel.currentYamnetLabel.value = top.label
                        viewModel.currentYamnetConfidence.value = top.score
                    } else {
                        viewModel.currentYamnetLabel.value = "No sound >0.3"
                        viewModel.currentYamnetConfidence.value = 0f
                    }

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
                delay(1000) // Logge jede Sekunde

                val now = System.currentTimeMillis()
                val timeStr = sdf.format(Date(now))

                // Hole alle aktuellen Werte aus dem ViewModel
                val env = viewModel.currentEnvironment.value ?: "Unknown"
                val envConf = viewModel.currentEnvironmentConfidence.value
                val act = viewModel.detectedActivity.value?.activityType ?: "Unknown"
                val actConf = viewModel.detectedActivity.value?.confidence ?: 0
                val yamnetLabel = viewModel.currentYamnetLabel.value
                val yamnetConf = viewModel.currentYamnetConfidence.value

                // CSV-Zeile zusammenbauen
                val csvLine = "$timeStr,$env,${"%.2f".format(envConf)},$act,$actConf,$yamnetLabel,${"%.2f".format(yamnetConf)}"

                // Log in ViewModel-Liste und in Datei
                viewModel.appendLog(csvLine)
            }
        }
    }
}
