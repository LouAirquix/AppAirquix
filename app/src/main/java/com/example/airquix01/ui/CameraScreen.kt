package com.example.airquix01.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.airquix01.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    var environmentText by remember { mutableStateOf("No Labels") }

    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.e("CameraScreen", "Camera permission not granted.")
            } else {
                if (!viewModel.cameraInService.value) {
                    // Vordergrundmodus: Kamera im Vordergrund starten
                    startForegroundCamera(context, activity, viewModel, previewView, onEnvChange = { environmentText = it }, onCameraProvider = { cameraProvider = it })
                } else {
                    // Hintergrundmodus: Service starten
                    ForegroundCameraService.startService(context)
                }
            }
        }

    // POST_NOTIFICATIONS Berechtigung anfragen wenn nötig
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (perm != PackageManager.PERMISSION_GRANTED) {
                val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Button(onClick = {
                if (viewModel.cameraInService.value) {
                    // Wechsel zu Vordergrund-Kamera
                    ForegroundCameraService.stopService(context)
                    viewModel.cameraInService.value = false
                    stopForegroundCamera(cameraProvider)
                    val cameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (cameraPerm == PackageManager.PERMISSION_GRANTED) {
                        startForegroundCamera(context, activity, viewModel, previewView, onEnvChange = { environmentText = it }, onCameraProvider = { cameraProvider = it })
                    } else {
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    // Wechsel zu Hintergrund-Kamera (Service)
                    stopForegroundCamera(cameraProvider)
                    val cameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (cameraPerm == PackageManager.PERMISSION_GRANTED) {
                        ForegroundCameraService.startService(context)
                        viewModel.cameraInService.value = true
                    } else {
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }) {
                Text(if (viewModel.cameraInService.value) "Switch to foreground camera mode" else "Switch to background camera mode")
            }
        }

        if (!viewModel.cameraInService.value) {
            // Vordergrundmodus: Demo-Modus
            // Keine Logs, kein Mismatch, nur Statusanzeige
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(UiColor(0x80000000))
                        .padding(8.dp)
                ) {
                    Text(
                        text = environmentText,
                        style = MaterialTheme.typography.bodyLarge.copy(color = UiColor.White)
                    )
                }
            }
        } else {
            // Hintergrundmodus
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera running in background service.\nLogs and mismatches are only checked in background mode.")
            }
        }
    }
}

private fun startForegroundCamera(
    context: Context,
    activity: ComponentActivity,
    viewModel: MainViewModel,
    previewView: PreviewView,
    onEnvChange: (String) -> Unit,
    onCameraProvider: (ProcessCameraProvider) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)
    cameraProviderFuture.addListener({
        val provider = cameraProviderFuture.get()
        onCameraProvider(provider)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // Im Vordergrundmodus nur Demo: Kein Logging, kein Mismatch
        analyzer.setAnalyzer(executor, ImageLabelAnalyzer { labels ->
            val env = EnvironmentDetector.detectEnvironment(labels)
            onEnvChange(env)
        })

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                activity,
                cameraSelector,
                preview,
                analyzer
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }

    }, executor)
}

private fun stopForegroundCamera(cameraProvider: ProcessCameraProvider?) {
    cameraProvider?.unbindAll()
}
