package com.example.airquix01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.airquix01.MainViewModel
import com.example.airquix01.ActivityRecognitionService
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Wichtig: Importiere getValue und setValue für die Delegation
import com.example.airquix01.AirquixApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val airquixApplication = context.applicationContext as AirquixApplication
    val mainViewModel = airquixApplication.getMainViewModel()
    val detectedActivityData = mainViewModel.detectedActivity.value

    // Log detectedActivityData
    LaunchedEffect(detectedActivityData) {
        Log.d("ActivityScreen", "detectedActivityData: $detectedActivityData")
    }

    // Berechtigungs-Launcher
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ActivityRecognitionService.startService(context)
            Toast.makeText(context, "Activity Recognition Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Activity Recognition", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (checkActivityRecognitionPermission(context)) {
                    ActivityRecognitionService.startService(context)
                    Toast.makeText(context, "Activity Recognition Started", Toast.LENGTH_SHORT).show()
                } else {
                    activityRecognitionPermissionLauncher.launch(getActivityRecognitionPermission())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Activity Recognition")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                ActivityRecognitionService.stopService(context)
                Toast.makeText(context, "Activity Recognition Stopped", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Activity Recognition")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Detected Activity:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (detectedActivityData != null) {
            Text("Activity: ${detectedActivityData.activityType}")
            Text("Confidence: ${detectedActivityData.confidence}%")
        } else {
            Text("No activity detected.")
        }
    }
}

// Hilfsfunktionen für Berechtigungen
fun getActivityRecognitionPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
}

fun checkActivityRecognitionPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        getActivityRecognitionPermission()
    ) == PackageManager.PERMISSION_GRANTED
}
