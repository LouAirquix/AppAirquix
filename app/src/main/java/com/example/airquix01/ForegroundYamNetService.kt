package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.*
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

/**
 * ForegroundYamNetService: Führt die YamNet-Audioklassifizierung
 * dauerhaft im Hintergrund durch.
 *
 * Erfordert RECORD_AUDIO Permission.
 */
class ForegroundYamNetService : LifecycleService() {

    private var audioRecord: AudioRecord? = null
    private var isClassifying = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        startYamNetClassification()
    }

    /**
     * Benachrichtigung für den Foreground Service
     */
    private fun startForegroundServiceNotification() {
        val channelId = "yamnet_service_channel"
        val channelName = "YamNet Background Service"
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
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("YamNet classification active")
            .setContentText("The app classifies audio in the background.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(4, notification)
    }

    /**
     * Startet die YamNet-Klassifizierung in einer Endlosschleife
     */
    private fun startYamNetClassification() {
        // Falls nicht schon klassifiziert wird
        if (isClassifying) return
        isClassifying = true

        val application = applicationContext as AirquixApplication
        val mainViewModel = application.getMainViewModel()

        serviceScope.launch {
            try {
                // Lade das Modell (muss in assets liegen)
                val classifier = AudioClassifier.createFromFile(this@ForegroundYamNetService,
                    "lite-model_yamnet_classification_tflite_1.tflite")
                val tensor = classifier.createInputTensorAudio()

                // AudioRecord erzeugen & starten
                val record = classifier.createAudioRecord()
                record.startRecording()
                audioRecord = record

                val probabilityThreshold = 0.3f
                while (isActive && isClassifying) {
                    // Audio in den Tensor laden
                    tensor.load(record)

                    // Klassifizieren
                    val output = classifier.classify(tensor)
                    val filteredModelOutput = output[0].categories.filter {
                        it.score >= probabilityThreshold
                    }.sortedByDescending { it.score }

                    // Bestes Ergebnis ans MainViewModel
                    val bestCategory = filteredModelOutput.firstOrNull()
                    if (bestCategory != null) {
                        mainViewModel.updateYamNetResult(bestCategory.label, bestCategory.score)
                    } else {
                        // Keine Klassifikation über dem Threshold => "No classification"
                        mainViewModel.updateYamNetResult("No classification", 0f)
                    }

                    delay(500)
                }
            } catch (e: Exception) {
                Log.e("ForegroundYamNetService", "Error: ${e.message}")
                mainViewModel.updateYamNetResult("Error: ${e.message}", 0f)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopClassification()
    }

    private fun stopClassification() {
        isClassifying = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, ForegroundYamNetService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundYamNetService::class.java)
            context.stopService(intent)
        }
    }
}
