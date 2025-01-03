package com.example.airquix01

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import android.util.Log

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            result?.probableActivities?.let { activities ->
                Log.d("ActivityRecognitionReceiver", "Received activity recognition result")

                // Finde die Aktivität mit der höchsten Wahrscheinlichkeit
                val mostProbableActivity = activities.maxByOrNull { it.confidence }

                mostProbableActivity?.let { activity ->
                    Log.d("ActivityRecognitionReceiver", "Detected Activity: ${activity.type}, Confidence: ${activity.confidence}%")

                    // Aktualisiere das ViewModel
                    val application = context.applicationContext as AirquixApplication
                    val viewModel = application.getMainViewModel()
                    viewModel.updateDetectedActivity(activity.type, activity.confidence)
                }
            } ?: run {
                Log.d("ActivityRecognitionReceiver", "No activities found in result")
            }
        } else {
            Log.d("ActivityRecognitionReceiver", "No activity recognition result found")
        }
    }
}
