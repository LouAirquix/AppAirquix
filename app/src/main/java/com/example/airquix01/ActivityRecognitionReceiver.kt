package com.example.airquix01

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActivityRecognitionReceiver(private val onActivityDetected: (ActivityData) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "ACTIVITY_RECOGNITION_DATA") {
            val activities = intent.getParcelableArrayListExtra<ActivityData>("activities")
            activities?.forEach { activity ->
                onActivityDetected(activity)
            }
        }
    }
}
