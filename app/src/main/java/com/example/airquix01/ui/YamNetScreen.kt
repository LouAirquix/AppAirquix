package com.example.airquix01.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.airquix01.AirquixApplication
import com.example.airquix01.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YamNetScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    var classificationResult by remember { mutableStateOf("No classification yet") }
    val probabilityThreshold = 0.3f

    val hasMicPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Mic permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val scope = rememberCoroutineScope()

    var audioRecord by remember { mutableStateOf<AudioRecord?>(null) }
    var isClassifying by remember { mutableStateOf(false) }

    val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"

    fun startClassification() {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isClassifying) return

        try {
            val classifier = AudioClassifier.createFromFile(context, modelPath)
            val tensor = classifier.createInputTensorAudio()

            val record = classifier.createAudioRecord()
            record.startRecording()
            audioRecord = record

            isClassifying = true
            classificationResult = "Listening..."

            scope.launch(Dispatchers.Default) {
                while (isClassifying) {
                    tensor.load(record)
                    val output = classifier.classify(tensor)
                    val filteredModelOutput = output[0].categories.filter {
                        it.score >= probabilityThreshold
                    }.sortedByDescending { it.score }

                    // NEU: Falls es ein Top-Ergebnis gibt => ans MainViewModel geben
                    val bestCategory = filteredModelOutput.firstOrNull()
                    if (bestCategory != null) {
                        val application = context.applicationContext as AirquixApplication
                        val mainViewModel = application.getMainViewModel()
                        mainViewModel.updateYamNetResult(bestCategory.label, bestCategory.score)
                    }

                    val resultStr = if (filteredModelOutput.isNotEmpty()) {
                        filteredModelOutput.joinToString("\n") {
                            "${it.label} (${String.format("%.2f", it.score)})"
                        }
                    } else {
                        "No sound with probability > $probabilityThreshold"
                    }
                    classificationResult = resultStr
                    delay(500)
                }
            }
        } catch (e: Exception) {
            Log.e("YamNetScreen", "Error initializing audio classifier: ${e.message}")
            classificationResult = "Error: ${e.message}"
            isClassifying = false
        }
    }

    fun stopClassification() {
        isClassifying = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        classificationResult = "Classification stopped."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YamNet Audio Classification",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = { startClassification() }, enabled = !isClassifying) {
                Text("Start")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { stopClassification() }, enabled = isClassifying) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Result:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = classificationResult,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
