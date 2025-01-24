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

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "Some permissions denied: $denied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Berechtigungen anfragen
        checkAndRequestPermissions()

        setContent {
            MymlkitappTheme {
                val context = LocalContext.current
                val app = context.applicationContext as AirquixApplication
                val viewModel = app.getMainViewModel()

                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = { Text("Airquix01 - LMU") }
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onStartLogging = { startLoggingService() },
                        onStopLogging = { stopLoggingService() },
                        onClearLogs = { viewModel.clearAllLogs() },
                        onShareLogs = { shareCsvLogs() },
                        onShareFeatureCsv = { shareFeatureCsv() }
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------
    // UI-Composables
    // -----------------------------------------------------------
    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        viewModel: MainViewModel,
        onStartLogging: () -> Unit,
        onStopLogging: () -> Unit,
        onClearLogs: () -> Unit,
        onShareLogs: () -> Unit,
        onShareFeatureCsv: () -> Unit
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
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
                    Text("Share CSV Logs")
                }
                Button(
                    onClick = onShareFeatureCsv,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share Feature CSV")
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Logs:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val logList = viewModel.logList
            if (logList.isEmpty()) {
                Text("No logs yet.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logList) { logLine ->
                        LogItem(logLine)
                    }
                }
            }
        }
    }

    /**
     * CSV-Parsing, damit wir die Felder korrekt erhalten,
     * selbst wenn ein Feld doppelte Anführungszeichen oder Kommata enthält.
     *
     * Hier ein stark vereinfachter CSV-Parser:
     * - Trennt Felder an Kommas,
     * - beachtet Anführungszeichen und escaped Quotes.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote "" -> füge ein " hinzu
                        current.append('"')
                        i++
                    } else {
                        // Toggle insideQuotes
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    // Feldende
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        // letztes Feld
        result.add(current.toString())
        return result
    }

    @Composable
    fun LogItem(logLine: String) {
        // Unsere CSV hat 11 Spalten:
        //  0: timestamp
        //  1: ENV
        //  2: ENV_confidence
        //  3: ACT
        //  4: ACT_confidence
        //  5: YAMNET_top1
        //  6: top1_conf
        //  7: YAMNET_top2
        //  8: top2_conf
        //  9: YAMNET_top3
        // 10: top3_conf
        val parts = remember(logLine) { parseCsvLine(logLine) }

        val timestamp = parts.getOrNull(0) ?: ""
        val env = parts.getOrNull(1) ?: ""
        val envConf = parts.getOrNull(2) ?: ""
        val act = parts.getOrNull(3) ?: ""
        val actConf = parts.getOrNull(4) ?: ""
        val top1Label = parts.getOrNull(5) ?: ""
        val top1Conf = parts.getOrNull(6) ?: ""
        val top2Label = parts.getOrNull(7) ?: ""
        val top2Conf = parts.getOrNull(8) ?: ""
        val top3Label = parts.getOrNull(9) ?: ""
        val top3Conf = parts.getOrNull(10) ?: ""

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Timestamp: $timestamp", style = MaterialTheme.typography.bodySmall)
                Text("Environment: $env (conf: $envConf)")
                Text("Activity: $act (conf: $actConf)")
                Text("Top-1: $top1Label (conf: $top1Conf)")
                Text("Top-2: $top2Label (conf: $top2Conf)")
                Text("Top-3: $top3Label (conf: $top3Conf)")
            }
        }
    }

    // -----------------------------------------------------------
    // Hilfsfunktionen
    // -----------------------------------------------------------

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
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

    private fun shareCsvLogs() {
        val vm = (applicationContext as AirquixApplication).getMainViewModel()
        val csvFile = vm.getLogsCsvFile()

        if (!csvFile.exists()) {
            Toast.makeText(this, "No CSV logs to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            packageName + ".provider",
            csvFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share CSV Logs"))
    }

    private fun shareFeatureCsv() {
        val vm = (applicationContext as AirquixApplication).getMainViewModel()
        val csvFile = vm.getFeatureCsvFile()

        if (!csvFile.exists()) {
            Toast.makeText(this, "No Feature CSV to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            packageName + ".provider",
            csvFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Feature CSV"))
    }
}
