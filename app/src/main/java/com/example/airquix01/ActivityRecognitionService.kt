package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import android.Manifest
import android.content.pm.PackageManager

class ActivityRecognitionService : LifecycleService() {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        startForegroundServiceNotification()
        requestActivityUpdates()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "activity_recognition_channel"
        val channelName = "Activity Recognition Service"
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
            .setContentTitle("Activity Recognition Active")
            .setContentText("Tracking your activities in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(3, notification)
    }

    private fun requestActivityUpdates() {
        // Überprüfe die Berechtigung vor der Anforderung
        if (!checkActivityRecognitionPermission()) {
            // Berechtigung nicht erteilt, Service beenden
            stopSelf()
            return
        }

        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            else
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Anfrage für Updates alle 10 Sekunden (für schnellere Tests)
        activityRecognitionClient.requestActivityUpdates(
            10000, // 10 Sekunden
            pendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (checkActivityRecognitionPermission()) {
            activityRecognitionClient.removeActivityUpdates(
                android.app.PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(this, ActivityRecognitionReceiver::class.java),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    else
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            Log.w("ActivityRecognitionService", "ACTIVITY_RECOGNITION permission not granted. Skipping removeActivityUpdates.")
        }
    }

    private fun checkActivityRecognitionPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            getActivityRecognitionPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getActivityRecognitionPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACTIVITY_RECOGNITION
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, ActivityRecognitionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ActivityRecognitionService::class.java)
            context.stopService(intent)
        }
    }
}
