package com.example.airquix01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.airquix01.AirquixApplication
import com.example.airquix01.ActivityLogEntry
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val airquixApplication = context.applicationContext as AirquixApplication
    val mainViewModel = airquixApplication.getMainViewModel()
    val detectedActivityData = mainViewModel.detectedActivity.value
    val activityLogs = mainViewModel.activityLogs

    // Log detectedActivityData
    LaunchedEffect(detectedActivityData) {
        Log.d("ActivityScreen", "detectedActivityData: $detectedActivityData")
    }

    // Berechtigungs-Launcher für Activity Recognition
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

    // Berechtigungs-Launcher für Speicher (zum Teilen als CSV) - nur für Android < Q erforderlich
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            shareLogsAsCsv(activityLogs, context)
        } else {
            Toast.makeText(context, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(24.dp))

        // Logs anzeigen
        Text("Activity Logs:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (activityLogs.isNotEmpty()) {
            // Verwende weight, um die LazyColumn den verfügbaren Platz einnehmen zu lassen
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Dynamische Höhe
            ) {
                items(activityLogs) { log ->
                    ActivityLogItem(log)
                    Divider()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { mainViewModel.clearActivityLogs() }) {
                    Text("Clear Logs")
                }

                Button(onClick = {
                    // Prüfe und fordere ggf. Speicherberechtigung an (nur für Android < Q)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            shareLogsAsCsv(activityLogs, context)
                        }
                    } else {
                        // Ab Android Q ist keine Speicherberechtigung für den internen Speicher erforderlich
                        shareLogsAsCsv(activityLogs, context)
                    }
                }) {
                    Text("Share as CSV")
                }
            }
        } else {
            Text("No logs available.")
        }
    }
}

@Composable
fun ActivityLogItem(log: ActivityLogEntry) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Activity: ${log.activityType}", style = MaterialTheme.typography.bodyLarge)
        Text("Confidence: ${log.confidence}%", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Time: ${formatTimestamp(log.timestamp)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
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

// Funktion zum Teilen der Logs als CSV
fun shareLogsAsCsv(logs: List<ActivityLogEntry>, context: android.content.Context) {
    if (logs.isEmpty()) {
        Toast.makeText(context, "No logs to share.", Toast.LENGTH_SHORT).show()
        return
    }

    val csvBuilder = StringBuilder()
    csvBuilder.append("Activity Type,Confidence,Timestamp\n")
    logs.forEach { log ->
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
        csvBuilder.append("${log.activityType},${log.confidence},$formattedTime\n")
    }

    try {
        val fileName = "activity_logs_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        val writer = FileWriter(file)
        writer.write(csvBuilder.toString())
        writer.flush()
        writer.close()

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/csv"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Activity Logs"))
    } catch (e: IOException) {
        Log.e("ActivityScreen", "Error creating CSV file", e)
        Toast.makeText(context, "Error creating CSV file.", Toast.LENGTH_SHORT).show()
    }
}
