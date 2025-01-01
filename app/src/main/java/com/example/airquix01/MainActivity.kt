package com.example.airquix01

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.airquix01.ui.AppScreens
import com.example.airquix01.ui.theme.MymlkitappTheme

class MainActivity : ComponentActivity() {
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.e("MainActivity", "Camera permission not granted.")
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Wenn cameraInService später true ist, kann service gestartet werden.
                // Vorerst starten wir keinen Service, sondern warten bis der Nutzer es im UI umschaltet.
            }
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.e("MainActivity", "POST_NOTIFICATIONS permission not granted.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kamera Berechtigung anfragen falls nicht vorhanden
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // POST_NOTIFICATIONS Berechtigung für Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MymlkitappTheme {
                val viewModel = remember { MainViewModel() }
                AppScreens(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: Service stoppen wenn gewünscht
        // ForegroundCameraService.stopService(this)
    }
}
