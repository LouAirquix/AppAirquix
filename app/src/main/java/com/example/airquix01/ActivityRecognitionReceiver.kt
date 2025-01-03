package com.example.airquix01

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val activities = result.probableActivities

            // Finde die Aktivität mit der höchsten Wahrscheinlichkeit
            val mostProbableActivity = activities.maxByOrNull { it.confidence }

            mostProbableActivity?.let { activity ->
                // Aktualisiere das ViewModel
                val application = context.applicationContext as AirquixApplication
                val viewModel = application.getViewModel()
                viewModel.updateDetectedActivity(activity.type, activity.confidence)
            }
        }
    }
}
