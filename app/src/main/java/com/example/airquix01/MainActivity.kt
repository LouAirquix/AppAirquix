package com.example.airquix01

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

                // State für Read-Me-Dialog
                var showReadMe by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = { Text("Airquix01App - LMU") },
                            actions = {
                                IconButton(onClick = { showReadMe = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Read Me"
                                    )
                                }
                            }
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

                    if (showReadMe) {
                        ReadMeDialog(
                            onDismiss = { showReadMe = false },
                            context = context
                        )
                    }
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
     * Einfacher CSV-Parser, der Anführungszeichen und Kommas korrekt behandelt.
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
        // Angepasst: Wir erwarten jetzt 15 Felder:
        // 0: timestamp
        // 1: PLACES_top1, 2: places_top1_conf
        // 3: PLACES_top2, 4: places_top2_conf
        // 5: ACT, 6: ACT_confidence
        // 7: YAMNET_top1, 8: top1_conf
        // 9: YAMNET_top2, 10: top2_conf
        // 11: YAMNET_top3, 12: top3_conf
        // 13: VEHICLE_label, 14: vehicle_conf
        val parts = remember(logLine) { parseCsvLine(logLine) }
        val timestamp = parts.getOrNull(0) ?: ""
        val placesTop1 = parts.getOrNull(1) ?: ""
        val placesTop1Conf = parts.getOrNull(2) ?: ""
        val placesTop2 = parts.getOrNull(3) ?: ""
        val placesTop2Conf = parts.getOrNull(4) ?: ""
        val act = parts.getOrNull(5) ?: ""
        val actConf = parts.getOrNull(6) ?: ""
        val yamTop1 = parts.getOrNull(7) ?: ""
        val yamTop1Conf = parts.getOrNull(8) ?: ""
        val yamTop2 = parts.getOrNull(9) ?: ""
        val yamTop2Conf = parts.getOrNull(10) ?: ""
        val yamTop3 = parts.getOrNull(11) ?: ""
        val yamTop3Conf = parts.getOrNull(12) ?: ""
        val vehLabel = parts.getOrNull(13) ?: ""
        val vehConf = parts.getOrNull(14) ?: ""

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
                Text("Places Top-1: $placesTop1 (conf: $placesTop1Conf)")
                Text("Places Top-2: $placesTop2 (conf: $placesTop2Conf)")
                Text("Activity: $act (conf: $actConf)")
                Text("YAMNET Top-1: $yamTop1 (conf: $yamTop1Conf)")
                Text("YAMNET Top-2: $yamTop2 (conf: $yamTop2Conf)")
                Text("YAMNET Top-3: $yamTop3 (conf: $yamTop3Conf)")
                Text("Vehicle (Top-1): $vehLabel (conf: $vehConf)")
            }
        }
    }

    @Composable
    fun ReadMeDialog(onDismiss: () -> Unit, context: Context) {
        val readMeContent = remember { loadReadMeFromAssets(context) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Read Me", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = readMeContent,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }

    fun loadReadMeFromAssets(context: Context): String {
        return try {
            val inputStream = context.assets.open("readme.txt")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer)
        } catch (e: Exception) {
            "Read-Me-Datei konnte nicht geladen werden."
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
