package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import android.app.Service
import android.os.IBinder

class BackgroundDetectedActivitiesService : Service() {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var detectionIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        val intent = Intent(this, ActivityDetectionIntentService::class.java)
        detectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Starten der Aktivitätserkennung
        activityRecognitionClient.requestActivityUpdates(
            10000, // Intervall in Millisekunden (10 Sekunden)
            detectionIntent
        ).addOnSuccessListener {
            // Erfolgreich gestartet
        }.addOnFailureListener {
            // Fehler beim Starten
            stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "activity_service_channel"
        val channelName = "Activity Recognition Service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Aktivitätserkennung aktiv")
            .setContentText("Die App erkennt Ihre Aktivitäten im Hintergrund.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(3, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stoppen der Aktivitätserkennung
        activityRecognitionClient.removeActivityUpdates(detectionIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundDetectedActivitiesService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundDetectedActivitiesService::class.java)
            context.stopService(intent)
        }
    }
}
