package com.example.airquix01

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.airquix01.ui.theme.MymlkitappTheme

/**
 * Hilfsklasse, die mittels AudioManager versucht, das Bluetooth-Mikro zu verwenden,
 * sofern eines als Input verfügbar ist.
 */
class AudioRoutingHelper(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun routeAudioToBluetoothIfAvailable() {
        // Hole alle Eingabegeräte
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        // Prüfe, ob ein Gerät vom Typ Bluetooth SCO (Headset) angeschlossen ist
        val bluetoothDeviceConnected = inputDevices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (bluetoothDeviceConnected) {
            // Setze den Audio-Modus auf Kommunikationsmodus
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // Starte SCO, sodass das Bluetooth-Mikro genutzt wird
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.d("AudioRoutingHelper", "Bluetooth SCO gestartet: Bluetooth-Mikro wird verwendet.")
        } else {
            Log.d("AudioRoutingHelper", "Kein Bluetooth-Mikro gefunden – Standard-Audiogerät wird verwendet.")
        }
    }

    fun stopBluetoothRouting() {
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d("AudioRoutingHelper", "Bluetooth SCO gestoppt.")
        }
    }
}

class MainActivity : ComponentActivity() {

    // Launcher für Berechtigungen
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private lateinit var audioRoutingHelper: AudioRoutingHelper

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        // AudioRoutingHelper initialisieren und versuchen, das Bluetooth-Mikro zu nutzen
        audioRoutingHelper = AudioRoutingHelper(this)
        audioRoutingHelper.routeAudioToBluetoothIfAvailable()

        setContent {
            MymlkitappTheme {
                val context = LocalContext.current
                // Hole das ViewModel über die Application-Klasse
                val app = context.applicationContext as AirquixApplication
                val viewModel = app.getMainViewModel()

                var showReadMe by remember { mutableStateOf(false) }
                var showStatusDialog by remember { mutableStateOf(false) }
                var showImageDialog by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        MyTopBar(
                            onReadmeClick = { showReadMe = true },
                            onImageButtonClick = { showImageDialog = true },
                            currentCapturedImage = viewModel.currentCapturedImage.value
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
                        onShowStatusDialog = { showStatusDialog = true }
                    )

                    if (showReadMe) {
                        ReadMeDialog(
                            onDismiss = { showReadMe = false },
                            context = context
                        )
                    }
                    if (showStatusDialog) {
                        StatusSelectionDialog(
                            onDismiss = { showStatusDialog = false },
                            onStatusSelected = { selectedStatus ->
                                viewModel.currentStatusGt.value = selectedStatus
                                showStatusDialog = false
                            }
                        )
                    }
                    if (showImageDialog) {
                        ImageDialog(
                            image = viewModel.currentCapturedImage.value,
                            onDismiss = { showImageDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun startLoggingService() {
        val intent = Intent(this, LoggingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLoggingService() {
        val intent = Intent(this, LoggingService::class.java)
        stopService(intent)
        // Stoppe ggf. das Bluetooth-Audio-Routing, wenn der Service beendet wird
        audioRoutingHelper.stopBluetoothRouting()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopBar(
    onReadmeClick: () -> Unit,
    onImageButtonClick: () -> Unit,
    currentCapturedImage: ImageBitmap?
) {
    SmallTopAppBar(
        title = { Text("AirquixApp - LMU", fontSize = 18.sp) },
        actions = {
            IconButton(onClick = onImageButtonClick) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "Show Captured Image",
                )
            }
            IconButton(onClick = onReadmeClick) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Read Me",
                )
            }
        }
    )
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onStartLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onClearLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onShowStatusDialog: () -> Unit
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
        Spacer(modifier = Modifier.height(16.dp))
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
        }
        Spacer(modifier = Modifier.height(16.dp))
        ElevatedButton(
            onClick = onShowStatusDialog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Status (status_gt)")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Current Speed: ${viewModel.currentSpeed.value} m/s",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Current Noise: ${"%.2f".format(viewModel.currentPegel.value)} dB",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Current Status: ${viewModel.currentStatusGt.value}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun StatusSelectionDialog(
    onDismiss: () -> Unit,
    onStatusSelected: (String) -> Unit
) {
    val statusOptions = listOf(
        "Vehicle in Tram",
        "Vehicle in Car",
        "Vehicle in Bus",
        "Vehicle in Subway",
        "Vehicle in E-Bus",
        "Vehicle in S-Bahn",
        "Vehicle in Subway (old)",
        "Outdoor by Bike",
        "Outdoor on Foot",
        "Outdoor in Nature",
        "Outdoor running",
        "Indoor with window open",
        "Indoor with window closed",
        "Indoor in Supermarket",
        "Indoor in Large-Room",
        "Indoor in Subway-Station"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Status") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                statusOptions.forEach { status ->
                    TextButton(onClick = { onStatusSelected(status) }) {
                        Text(status)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LogItem(logLine: String) {
    val parts = remember(logLine) { parseCsvLine(logLine) }
    val trimmedParts = if (parts.size > 26) parts.take(26) else parts

    val timestamp = trimmedParts.getOrNull(0) ?: ""
    val placesTop1 = trimmedParts.getOrNull(1) ?: ""
    val placesTop1Conf = trimmedParts.getOrNull(2) ?: ""
    val placesTop2 = trimmedParts.getOrNull(3) ?: ""
    val placesTop2Conf = trimmedParts.getOrNull(4) ?: ""
    val placesTop3 = trimmedParts.getOrNull(5) ?: ""
    val placesTop3Conf = trimmedParts.getOrNull(6) ?: ""
    val placesTop4 = trimmedParts.getOrNull(7) ?: ""
    val placesTop4Conf = trimmedParts.getOrNull(8) ?: ""
    val placesTop5 = trimmedParts.getOrNull(9) ?: ""
    val placesTop5Conf = trimmedParts.getOrNull(10) ?: ""
    val sceneType = trimmedParts.getOrNull(11) ?: ""
    val act = trimmedParts.getOrNull(12) ?: ""
    val actConf = trimmedParts.getOrNull(13) ?: ""
    val yamTop1 = trimmedParts.getOrNull(14) ?: ""
    val yamTop1Conf = trimmedParts.getOrNull(15) ?: ""
    val yamTop2 = trimmedParts.getOrNull(16) ?: ""
    val yamTop2Conf = trimmedParts.getOrNull(17) ?: ""
    val yamTop3 = trimmedParts.getOrNull(18) ?: ""
    val yamTop3Conf = trimmedParts.getOrNull(19) ?: ""
    val vehLabel = trimmedParts.getOrNull(20) ?: ""
    val vehConf = trimmedParts.getOrNull(21) ?: ""
    val newModelLabel = trimmedParts.getOrNull(22) ?: ""
    val newModelConf = trimmedParts.getOrNull(23) ?: ""
    val speedVal = trimmedParts.getOrNull(24) ?: ""
    val pegel = trimmedParts.getOrNull(25) ?: ""

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
            Text("Places Top-3: $placesTop3 (conf: $placesTop3Conf)")
            Text("Places Top-4: $placesTop4 (conf: $placesTop4Conf)")
            Text("Places Top-5: $placesTop5 (conf: $placesTop5Conf)")
            Text("Scene Type: $sceneType")
            Text("Activity: $act (conf: $actConf)")
            Text("YAMNET Top-1: $yamTop1 (conf: $yamTop1Conf)")
            Text("YAMNET Top-2: $yamTop2 (conf: $yamTop2Conf)")
            Text("YAMNET Top-3: $yamTop3 (conf: $yamTop3Conf)")
            Text("Vehicle Audio: $vehLabel (conf: $vehConf)")
            Text("Vehicle Image: $newModelLabel (conf: $newModelConf)")
            Text("Speed (m/s): $speedVal")
            Text("Noise (dB): $pegel")
        }
    }
}

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

@Composable
fun ImageDialog(
    image: ImageBitmap?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Captured Image", fontWeight = FontWeight.Bold) },
        text = {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Captured Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            } else {
                Text("No image available.")
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
