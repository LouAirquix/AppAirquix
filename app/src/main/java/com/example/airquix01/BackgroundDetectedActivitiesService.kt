package com.example.airquix01

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient

class BackgroundDetectedActivitiesService : Service() {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var detectionIntent: PendingIntent

    companion object {
        private const val TAG = "BgActivitiesSvc" // 16 Zeichen

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundDetectedActivitiesService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundDetectedActivitiesService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        val intent = Intent(this, ActivityDetectionIntentService::class.java)
        detectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        startForegroundServiceNotification()
    }

    @SuppressLint("MissingPermission") // Richtig: Über der Methode
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            try {
                activityRecognitionClient.requestActivityUpdates(
                    10000, // Intervall in Millisekunden (10 Sekunden)
                    detectionIntent
                ).addOnSuccessListener {
                    Log.d(TAG, "Aktivitätserkennung erfolgreich gestartet.")
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Fehler beim Starten der Aktivitätserkennung: ${exception.message}")
                    stopSelf()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                stopSelf()
            }
        } else {
            Log.e(TAG, "ACTIVITY_RECOGNITION Berechtigung fehlt.")
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

    @SuppressLint("MissingPermission") // Richtig: Über der Methode
    override fun onDestroy() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            try {
                activityRecognitionClient.removeActivityUpdates(detectionIntent)
                    .addOnSuccessListener {
                        Log.d(TAG, "Aktivitätserkennung erfolgreich gestoppt.")
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Fehler beim Stoppen der Aktivitätserkennung: ${exception.message}")
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException beim Stoppen der Aktivitätserkennung: ${e.message}")
            }
        } else {
            Log.e(TAG, "ACTIVITY_RECOGNITION Berechtigung fehlt beim Stoppen.")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
