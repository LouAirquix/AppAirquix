package com.example.airquix01.ui

import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.airquix01.*
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityRecognitionScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // Zugriff auf die ViewModel-StateFlow und Konvertierung zu Composable-States
    val currentActivity by viewModel.currentActivity.collectAsState()
    val confidenceLevel by viewModel.activityConfidence.collectAsState()

    // Berechtigung Launcher
    val requestActivityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                // Berechtigung nicht erteilt
                // Optional: Benutzer informieren
            }
        }
    )

    // Berechtigung überprüfen und anfordern
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestActivityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // Empfänger registrieren
    DisposableEffect(Unit) {
        val receiver = ActivityRecognitionReceiver { activityData ->
            val activityName = getActivityName(activityData.type)
            viewModel.updateActivityData(activityName, activityData.confidence)
        }
        val intentFilter = IntentFilter("ACTIVITY_RECOGNITION_DATA")
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilter)

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Aktivitätserkennung", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                // Starten des Services
                BackgroundDetectedActivitiesService.startService(context)
            }) {
                Text("Start")
            }

            Button(onClick = {
                // Stoppen des Services
                BackgroundDetectedActivitiesService.stopService(context)
            }) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Aktuelle Aktivität: $currentActivity", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Vertrauensniveau: $confidenceLevel%", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun getActivityName(activityType: Int): String {
    return when (activityType) {
        DetectedActivity.IN_VEHICLE -> "Im Fahrzeug"
        DetectedActivity.ON_BICYCLE -> "Auf dem Fahrrad"
        DetectedActivity.RUNNING -> "Laufen"
        DetectedActivity.WALKING -> "Gehen"
        DetectedActivity.STILL -> "Still"
        DetectedActivity.TILTING -> "Neigen"
        DetectedActivity.UNKNOWN -> "Unbekannt"
        DetectedActivity.ON_FOOT -> "Zu Fuß"
        else -> "Unbekannt"
    }
}
