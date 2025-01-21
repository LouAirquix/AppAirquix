package com.example.airquix01

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.airquix01.ui.theme.MymlkitappTheme

class MainActivity : ComponentActivity() {

    // Berechtigungs-Launcher z.B. für Kamera
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Schau, ob alles erteilt wurde
        val denied = permissions.filterValues { !it }
        if (denied.isNotEmpty()) {
            // Einige Berechtigungen nicht erteilt
            Toast.makeText(this, "Some permissions denied: $denied", Toast.LENGTH_SHORT).show()
        } else {
            // Alles ok
            Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zur Sicherheit: Kamera, Mikrofon, Activity Recognition anfragen
        checkAndRequestPermissions()

        setContent {
            MymlkitappTheme {
                val context = LocalContext.current
                val app = context.applicationContext as AirquixApplication
                val viewModel = app.getMainViewModel()

                // Einfaches Layout: Buttons + Logs
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Simple Logging App", style = MaterialTheme.typography.titleLarge)

                        Spacer(Modifier.height(16.dp))

                        // Start/Stop Button
                        Row {
                            Button(
                                onClick = {
                                    // Start Service
                                    startLoggingService()
                                },
                                enabled = !viewModel.isLogging.value
                            ) {
                                Text("Start")
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    // Stop Service
                                    stopLoggingService()
                                },
                                enabled = viewModel.isLogging.value
                            ) {
                                Text("Stop")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Clear und Share
                        Row {
                            Button(onClick = { viewModel.clearLogs() }) {
                                Text("Clear Logs")
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(onClick = { shareCsvFile() }) {
                                Text("Share CSV")
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Logs (einfach als Text)
                        Text("Logs:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        val logList = viewModel.logList
                        if (logList.isEmpty()) {
                            Text("No logs yet.")
                        } else {
                            for (logLine in logList) {
                                Text(logLine)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        // Kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        // Mikrofon
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        // Activity Recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        // Post Notifications (ab Android 13)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startLoggingService() {
        val intent = Intent(this, LoggingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLoggingService() {
        val intent = Intent(this, LoggingService::class.java)
        stopService(intent)
    }

    private fun shareCsvFile() {
        val context = this
        val app = applicationContext as AirquixApplication
        val csvFile = app.getMainViewModel().getCsvFile()

        if (!csvFile.exists()) {
            Toast.makeText(context, "No CSV to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            packageName + ".provider",
            csvFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share CSV"))
    }
}
