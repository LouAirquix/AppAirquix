package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ForegroundAudioService : LifecycleService() {
    private var recorder: MediaRecorder? = null
    private var startTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        // starte den Service mit einer Benachrichtigung
        startForegroundServiceNotification()
        // beginne mit der Aufnahme weil wer braucht schon Privatsphäre?
        startRecording()
    }

    /**
     * erstellt eine dauerhafte Benachrichtigung für den Vordergrundservice.
     * weil android der meinung ist dass alle deine aktivitäten durchschaubar sein sollten..
     */

    private fun startForegroundServiceNotification() {
        val channelId = "audio_service_channel"
        val channelName = "Audio Background Service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Audio recording active")
            .setContentText("The app records audio in the background.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        // Starte den Vordergrundservice mit der erstellten Benachrichtigung
        startForeground(2, notification)
    }

    /**
     * beginnt mit der Audioaufnahme
     */

    private fun startRecording() {
        val audioDir = getExternalFilesDir(null)
        val audioFile = File(audioDir, "ambient_audio.m4a")

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // Format, das niemand wirklich versteht
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100) // CD-Qualität, weil wir nur das Beste wollen
            setAudioEncodingBitRate(96000) //sorgt für gute Bildrate
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        startTime = System.currentTimeMillis()
        logTimestamp(startTime, "START") // Logge den Start
    }
    /**
     * stoppt die Audioaufnahme und gibt Ressourcen frei.
     * weil selbst geräte mal ne pause brauchen
     */
    private fun stopRecording() {
        recorder?.apply {
            stop() // Beende die Aufnahme
            release()
        }
        recorder = null
        val endTime = System.currentTimeMillis()
        logTimestamp(endTime, "END")
    }

    /**
     * loggt einen Zeitstempel und ein Ereignis in eine CSV-Datei
     */

    private fun logTimestamp(time: Long, event: String) {
        val audioDir = getExternalFilesDir(null)
        val csvFile = File(audioDir, "audio_timestamps.csv")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestampStr = sdf.format(Date(time))
        FileWriter(csvFile, true).use {
            it.append("$timestampStr,$event\n") // füge nen neuen eintrag hinzu weil wir keine leeren seiten mögen
        }
    }

    override fun onDestroy() {
        // Stoppe die Aufnahme
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        /**
         * Startet den Audio-Hintergrundservice
         */
        fun startService(context: Context) {
            val intent = Intent(context, ForegroundAudioService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        /**
         * Stoppt den Audio-Hintergrundservice
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundAudioService::class.java)
            context.stopService(intent)
        }
    }
}
