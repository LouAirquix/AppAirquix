package com.example.airquix01

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityRecognitionResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ActivityDetectionIntentService : IntentService("ActivityDetectionIntentService") {

    companion object {
        private const val TAG = "ActDetectSvc" // 11 Zeichen
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            try {
                val result = ActivityRecognitionResult.extractResult(intent)
                val detectedActivities = result?.probableActivities ?: emptyList()

                if (detectedActivities.isNotEmpty()) {
                    Log.d(TAG, "Erkannte Aktivitäten: $detectedActivities")
                    sendActivitiesBroadcast(detectedActivities)
                } else {
                    Log.d(TAG, "Keine Aktivitäten erkannt.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Verarbeiten des Intents: ${e.message}")
            }
        } else {
            Log.e(TAG, "Intent ist null")
        }
    }

    private fun sendActivitiesBroadcast(detectedActivities: List<DetectedActivity>) {
        try {
            val intent = Intent("ACTIVITY_RECOGNITION_DATA")
            val activities = detectedActivities.map { activity ->
                ActivityData(activity.type, activity.confidence)
            }
            intent.putParcelableArrayListExtra("activities", ArrayList(activities))
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "Broadcast gesendet.")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Senden des Broadcasts: ${e.message}")
        }
    }
}
