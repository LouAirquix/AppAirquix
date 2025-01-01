package com.example.airquix01.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.airquix01.CsvLogger
import com.example.airquix01.MainViewModel
import com.example.airquix01.PrefsHelper
import com.example.airquix01.ServicePrefsHelper
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val manualLogger = remember { CsvLogger(context, "manual_environment_logs.csv") }
    var logs by remember { mutableStateOf(manualLogger.readLogs()) }

    val isBackgroundMode = viewModel.cameraInService.value
    val scrollState = rememberScrollState()

    var expanded by remember { mutableStateOf(false) }
    var newEnvText by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Inside") }
    val categories = listOf("Inside", "Outside")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Text("Manual", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        if (isBackgroundMode) {
            Button(onClick = {
                viewModel.loggingEnabledManual.value = !viewModel.loggingEnabledManual.value
                PrefsHelper.setLoggingManual(context, viewModel.loggingEnabledManual.value)
            }) {
                Text(if (viewModel.loggingEnabledManual.value) "Stop Manual" else "Start Manual")
            }
        } else {
            Text("Manual logging needs background mode.")
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                shareCsv(context, manualLogger)
            }) {
                Text("Share CSV")
            }

            Button(onClick = {
                manualLogger.deleteLogs()
                logs = manualLogger.readLogs()
            }) {
                Text("Delete CSV")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Select Status:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row {
            Box {
                Button(onClick = { expanded = true }) {
                    Text(viewModel.currentManualEnvironment ?: "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    viewModel.predefinedEnvironments.forEach { env ->
                        DropdownMenuItem(
                            text = { Text(env) },
                            onClick = {
                                viewModel.setManualEnvironment(env)
                                if (isBackgroundMode) {
                                    PrefsHelper.setManualEnv(context, env)
                                    ServicePrefsHelper.setManualEnvCategory(context, env)
                                }
                                expanded = false
                            }
                        )
                    }
                    viewModel.customEnvironments.forEach { ce ->
                        DropdownMenuItem(
                            text = { Text(ce.name) },
                            onClick = {
                                viewModel.setManualEnvironment(ce.name)
                                if (isBackgroundMode) {
                                    PrefsHelper.setManualEnv(context, ce.name)
                                    ServicePrefsHelper.setManualEnvCategory(context, ce.category)
                                }
                                expanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            viewModel.setManualEnvironment(null)
                            if (isBackgroundMode) {
                                PrefsHelper.setManualEnv(context, null)
                                ServicePrefsHelper.setManualEnvCategory(context, null)
                            }
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Add new status:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Name:")
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = newEnvText,
            onValueChange = { newEnvText = it },
            modifier = Modifier
                .background(Color.LightGray)
                .padding(8.dp)
                .fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))
        Text("Category:")
        Spacer(Modifier.height(4.dp))
        Row {
            Box {
                Button(onClick = { categoryExpanded = true }) {
                    Text(selectedCategory)
                }
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategory = cat
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = {
                val text = newEnvText.trim()
                if (text.isNotEmpty()) {
                    viewModel.addCustomEnvironment(text, selectedCategory)
                    newEnvText = ""
                }
            }) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(24.dp))

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
            logs = manualLogger.readLogs()
        }
    }
}

private fun shareCsv(context: Context, logger: CsvLogger) {
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
