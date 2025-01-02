package com.example.airquix01

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityRecognitionResult
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ActivityDetectionIntentService : IntentService("ActivityDetectionIntentService") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val result = ActivityRecognitionResult.extractResult(intent)
            // ... rest of your code
        } else {
            // Handle the case where the intent is null
            Log.e("ActivityDetection", "Intent is null")
        }
    }

    private fun sendActivitiesBroadcast(detectedActivities: List<DetectedActivity>) {
        val intent = Intent("ACTIVITY_RECOGNITION_DATA")
        val activities = detectedActivities.map { activity ->
            ActivityData(activity.type, activity.confidence)
        }
        intent.putParcelableArrayListExtra("activities", ArrayList(activities))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
