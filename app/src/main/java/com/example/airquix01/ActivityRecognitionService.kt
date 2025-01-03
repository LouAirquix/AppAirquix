package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity

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

        // Anfrage für Updates alle 5 Minuten (300000 ms)
        activityRecognitionClient.requestActivityUpdates(
            300000,
            pendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
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
