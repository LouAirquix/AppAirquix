package com.example.airquix01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.airquix01.MainViewModel
import com.example.airquix01.ActivityRecognitionService
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val detectedActivity by remember { derivedStateOf { viewModel.detectedActivity } }

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
                    activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
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

        if (detectedActivity != null) {
            Text("Activity: ${detectedActivity?.activityType}")
            Text("Confidence: ${detectedActivity?.confidence}%")
        } else {
            Text("No activity detected.")
        }
    }
}

fun checkActivityRecognitionPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
