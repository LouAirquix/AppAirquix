package com.example.airquix01.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.airquix01.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllInOneScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val application = context.applicationContext as AirquixApplication

    // CSV Logger für den neuen "All-in-One"-Tab
    val allInOneLogger = remember { CsvLogger(context, "all_in_one_logs.csv") }

    // State, um die Logs aus der CSV anzuzeigen
    var logs by remember { mutableStateOf(allInOneLogger.readLogs()) }

    // Steuert, ob wir periodisch loggen
    var isLogging by remember { mutableStateOf(false) }

    // Coroutine-Scope für Logging
    val scope = rememberCoroutineScope()

    // Alle 2 Sekunden (solange isLogging=true) -> CSV schreiben
    LaunchedEffect(isLogging) {
        while (isLogging) {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))

            val aiEnv = viewModel.lastAiEnv.value
            val activityData = viewModel.detectedActivity.value
            val yamNetLabel = viewModel.lastYamNetLabel.value
            val yamNetConf = viewModel.lastYamNetConfidence.value

            // Spalten definieren:
            // time, AIEnv, ActivityType, ActConf, YamNetLabel, YamNetConf
            val line = "$time,$aiEnv,${activityData?.activityType ?: "NoActivity"}," +
                    "${activityData?.confidence ?: 0},$yamNetLabel," +
                    String.format("%.2f", yamNetConf)

            // Ins CSV loggen
            allInOneLogger.logStatus(line)
            // Logs aktualisieren (UI)
            logs = allInOneLogger.readLogs()

            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("All-in-One Logging", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop Button
        Button(
            onClick = {
                if (!isLogging) {
                    // Wenn wir starten, dann:
                    // 1. Kamera im Hintergrund starten (falls nicht schon)
                    if (!viewModel.cameraInService.value) {
                        // Setze Flag & starte Service
                        viewModel.cameraInService.value = true
                        ForegroundCameraService.startService(context)
                    }
                    // 2. ActivityRecognitionService
                    ActivityRecognitionService.startService(context)
                    // 3. ForegroundYamNetService
                    ForegroundYamNetService.startService(context)

                    isLogging = true
                } else {
                    // STOP:
                    // 1. Kamera
                    if (viewModel.cameraInService.value) {
                        viewModel.cameraInService.value = false
                        ForegroundCameraService.stopService(context)
                    }
                    // 2. ActivityRecognition
                    ActivityRecognitionService.stopService(context)
                    // 3. YamNet
                    ForegroundYamNetService.stopService(context)

                    isLogging = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLogging) "Stop Logging" else "Start Logging")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons "Share CSV" & "Clear Log"
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { shareCsvFile(context, allInOneLogger) }) {
                Text("Share CSV")
            }
            Button(onClick = {
                allInOneLogger.deleteLogs()
                logs = allInOneLogger.readLogs()
            }) {
                Text("Clear Log")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text("No logs found.")
        } else {
            LazyColumn {
                items(logs) { line ->
                    Text(line)
                }
            }
        }
    }
}

private fun shareCsvFile(context: android.content.Context, logger: CsvLogger) {
    val file = logger.getFile()
    if (!file.exists()) return

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share All-in-One Logs"))
}
