package com.example.airquix01

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.airquix01.ui.theme.MymlkitappTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    // Berechtigungs-Launcher für mehrere Permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
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
                // Aus dem Application-Objekt das ViewModel holen
                val context = LocalContext.current
                val app = context.applicationContext as AirquixApplication
                val viewModel = app.getMainViewModel()

                // Scaffold für ein grundlegendes Layout (TopAppBar + Content)
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = { Text("Airquix01 - LMU") }
                        )
                    }
                ) { innerPadding ->
                    // Hauptbildschirm mit Buttons und Log-Liste
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onStartLogging = { startLoggingService() },
                        onStopLogging = { stopLoggingService() },
                        onClearLogs = { viewModel.clearLogs() },
                        onShareLogs = { shareCsvFile() }
                    )
                }
            }
        }
    }

    /**
     * Zeigt die Hauptoberfläche mit Start-/Stop-Buttons, Clear-/Share-Buttons und den Logs in einer LazyColumn.
     */
    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        viewModel: MainViewModel,
        onStartLogging: () -> Unit,
        onStopLogging: () -> Unit,
        onClearLogs: () -> Unit,
        onShareLogs: () -> Unit
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Entfernt: "Simple Logging App"

            // Erste Button-Reihe (Start/Stop Logging) füllt gesamte Breite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartLogging,
                    enabled = !viewModel.isLogging.value,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start")
                }
                Button(
                    onClick = onStopLogging,
                    enabled = viewModel.isLogging.value,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Zweite Button-Reihe (Clear/Share) füllt gesamte Breite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Logs")
                }
                Button(
                    onClick = onShareLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share CSV")
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Logs:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val logList = viewModel.logList
            if (logList.isEmpty()) {
                Text("No logs yet.")
            } else {
                // Anzeigen der Logs in einer LazyColumn
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logList) { logLine ->
                        LogItem(logLine)
                    }
                }
            }
        }
    }

    /**
     * Zeigt einen einzelnen Log-Eintrag in einer Card mit etwas Abstand.
     * Wir zerlegen den CSV-String grob und ordnen die Felder an.
     */
    @Composable
    fun LogItem(logLine: String) {
        // CSV-Aufbau: timestamp,ENV,ENV_confidence,ACT,ACT_confidence,YAMNET_label,YAMNET_confidence
        val parts = remember(logLine) { logLine.split(",") }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Timestamp: ${parts.getOrNull(0) ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Environment: ${parts.getOrNull(1) ?: "?"} (conf: ${parts.getOrNull(2) ?: "?"})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Activity: ${parts.getOrNull(3) ?: "?"} (conf: ${parts.getOrNull(4) ?: "?"})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Audio: ${parts.getOrNull(5) ?: "?"} (conf: ${parts.getOrNull(6) ?: "?"})",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    /**
     * Fordert die wichtigsten Berechtigungen an, sofern sie nicht vorhanden sind.
     */
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
