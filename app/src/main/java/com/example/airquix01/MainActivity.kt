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
        checkAndRequestPermissions()

        setContent {
            MymlkitappTheme {
                val context = LocalContext.current
                val app = context.applicationContext as AirquixApplication
                val viewModel = app.getMainViewModel()

                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = { Text("Airquix01App - LMU") }
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        onStartLogging = { startLoggingService() },
                        onStopLogging = { stopLoggingService() },
                        onClearLogs = { viewModel.clearAllLogs() },
                        onShareLogs = { shareLogsCsv() },
                        onShareFeatureCsv = { shareFeatureCsv() }
                    )
                }
            }
        }
    }

    // ---------------------------
    // UI
    // ---------------------------
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
            // Erste Reihe: Start & Stop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedButton(
                    onClick = onStartLogging,
                    enabled = !viewModel.isLogging.value,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start")
                }
                ElevatedButton(
                    onClick = onStopLogging,
                    enabled = viewModel.isLogging.value,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Zweite Reihe: Clear, Share CSV Logs, Share Feature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElevatedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Logs")
                }
                ElevatedButton(
                    onClick = onShareLogs,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share CSV Logs")
                }
                ElevatedButton(
                    onClick = onShareFeatureCsv,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share Feature")
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
     * Einfacher CSV-Parser, damit wir Anführungszeichen und Kommas sauber berücksichtigen können.
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
                    // Wenn innen und das nächste ist ebenfalls ", dann ist es escaped
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    @Composable
    fun LogItem(logLine: String) {
        // 11 Felder: time, env, env_conf, act, act_conf, top1, top1_conf, top2, top2_conf, top3, top3_conf
        val parts = remember(logLine) { parseCsvLine(logLine) }
        val timeStr = parts.getOrNull(0) ?: ""
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
                Text("Timestamp: $timeStr", style = MaterialTheme.typography.bodySmall)
                Text("Environment: $env (conf: $envConf)")
                Text("Activity: $act (conf: $actConf)")
                Text("Top-1: $top1Label (conf: $top1Conf)")
                Text("Top-2: $top2Label (conf: $top2Conf)")
                Text("Top-3: $top3Label (conf: $top3Conf)")
            }
        }
    }

    // ---------------------------
    // Berechtigungen
    // ---------------------------
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

    private fun shareLogsCsv() {
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
