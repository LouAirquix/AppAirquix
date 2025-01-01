package com.example.airquix01.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.airquix01.ForegroundAudioService
import com.example.airquix01.MainViewModel
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherInputsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val audioDir = context.getExternalFilesDir(null)
    val audioFile = File(audioDir, "ambient_audio.m4a")
    val csvFile = File(audioDir, "audio_timestamps.csv")

    var isAudioServiceRunning by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted: Boolean ->
            hasAudioPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Audio", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Record audio in background.")
        Spacer(Modifier.height(16.dp))

        if (!hasAudioPermission) {
            Text("No mic permission.")
        } else {
            Row {
                Button(onClick = {
                    ForegroundAudioService.startService(context)
                    isAudioServiceRunning = true
                }, enabled = !isAudioServiceRunning) {
                    Text("Start BG Audio")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    ForegroundAudioService.stopService(context)
                    isAudioServiceRunning = false
                }, enabled = isAudioServiceRunning) {
                    Text("Stop BG Audio")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (audioFile.exists() || csvFile.exists()) {
                Text("Files:")
                if (audioFile.exists()) Text("Audio: ${audioFile.name}")
                if (csvFile.exists()) Text("Timestamps: ${csvFile.name}")

                Spacer(Modifier.height(16.dp))
                Row {
                    Button(onClick = {
                        shareFiles(context, listOfNotNull(
                            audioFile.takeIf { it.exists() },
                            csvFile.takeIf { it.exists() }
                        ))
                    }) {
                        Text("Share")
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = {
                        if (audioFile.exists()) audioFile.delete()
                        if (csvFile.exists()) csvFile.delete()
                    }) {
                        Text("Delete")
                    }
                }
            } else {
                Text("No recording yet.")
            }
        }
    }
}

private fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        val uris = files.map {
            FileProvider.getUriForFile(context, context.packageName + ".provider", it)
        }
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Files"))
}
