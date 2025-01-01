package com.example.airquix01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.airquix01.MainViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import com.example.airquix01.CsvLogger
import com.example.airquix01.PrefsHelper
import com.example.airquix01.ServicePrefsHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val aiLogger = remember { CsvLogger(context, "ai_environment_logs.csv") }
    var logs by remember { mutableStateOf(aiLogger.readLogs()) }

    val isBackgroundMode = viewModel.cameraInService.value
    var mismatchEnabled by remember { mutableStateOf(ServicePrefsHelper.isMismatchEnabled(context)) }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("AI Logs", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        if (isBackgroundMode) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Ai Mismatch Assist:")
                Switch(
                    checked = mismatchEnabled,
                    onCheckedChange = { checked: Boolean ->
                        mismatchEnabled = checked
                        ServicePrefsHelper.setMismatchEnabled(context, checked)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = {
                viewModel.loggingEnabledAi.value = !viewModel.loggingEnabledAi.value
                PrefsHelper.setLoggingAi(context, viewModel.loggingEnabledAi.value)
            }) {
                Text(if (viewModel.loggingEnabledAi.value) "Stop AI" else "Start AI")
            }
        } else {
            Text("AI logging and mismatch need background mode.")
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { shareCsv(context, aiLogger) }) {
                Text("Share CSV")
            }

            Button(onClick = {
                aiLogger.deleteLogs()
                logs = aiLogger.readLogs()
            }) {
                Text("Delete CSV")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text("No logs")
        } else {
            logs.forEach { item ->
                Text(item)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            logs = aiLogger.readLogs()
        }
    }
}

private fun shareCsv(context: android.content.Context, logger: CsvLogger) {
    val file = logger.getFile()
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CSV"))
}
