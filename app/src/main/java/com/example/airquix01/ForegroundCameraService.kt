package com.example.airquix01

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * ServicePrefsHelper: um Einstellungen zu speichern (nicht sehr spannend)
 */
object ServicePrefsHelper {
    private const val KEY_MANUAL_ENV_CATEGORY = "manual_env_category"
    fun setManualEnvCategory(context: Context, category: String?) {
        PrefsHelper.getPrefs(context).edit().putString(KEY_MANUAL_ENV_CATEGORY, category ?: "").apply()
    }
    fun getManualEnvCategory(context: Context) =
        PrefsHelper.getPrefs(context).getString(KEY_MANUAL_ENV_CATEGORY, "")?.takeIf { it.isNotEmpty() }

    private const val KEY_MISMATCH_ENABLED = "mismatch_enabled"
    fun setMismatchEnabled(context: Context, enabled: Boolean) {
        PrefsHelper.getPrefs(context).edit().putBoolean(KEY_MISMATCH_ENABLED, enabled).apply()
    }
    fun isMismatchEnabled(context: Context) =
        PrefsHelper.getPrefs(context).getBoolean(KEY_MISMATCH_ENABLED, true)
}

class ForegroundCameraService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val aiLogger by lazy { CsvLogger(this, "ai_environment_logs.csv") }
    private val manualLogger by lazy { CsvLogger(this, "manual_environment_logs.csv") }

    private var lastLogTime = 0L

    private var mismatchCount = 0
    private var lastMismatchStartTime = 0L
    private var mismatchNotificationSent = false
    private val longMismatchDuration = 30_000L

    private var currentAiEnv: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupCamera()
        startPeriodicCheck()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "camera_service_channel"
        val channelName = "Camera Background Service"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Environment detection active")
            .setContentText("The app continues to recognise your surroundings in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build()
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                ImageLabelAnalyzer { labels ->
                    currentAiEnv = EnvironmentDetector.detectEnvironment(labels)

                    // NEU: AI-Umgebung ans MainViewModel weitergeben
                    val application = applicationContext as AirquixApplication
                    val mainViewModel = application.getMainViewModel()
                    mainViewModel.updateAiEnv(currentAiEnv)

                    if (ServicePrefsHelper.isMismatchEnabled(this)) {
                        checkMismatch(currentAiEnv)
                    } else {
                        resetMismatch()
                    }
                }
            )

            preview.setSurfaceProvider(DummySurfaceProvider(this))

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analyzer
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startPeriodicCheck() {
        serviceScope.launch {
            while (isActive) {
                delay(2000)
                handleLogging()
            }
        }
    }

    private fun handleLogging() {
        val loggingAi = PrefsHelper.isLoggingAi(this)
        val loggingManual = PrefsHelper.isLoggingManual(this)
        val manualEnv = PrefsHelper.getManualEnv(this)
        val manualEnvCategory = ServicePrefsHelper.getManualEnvCategory(this)

        val now = System.currentTimeMillis()
        if ((loggingAi || loggingManual) && now - lastLogTime >= 10_000) {
            lastLogTime = now
            // AI-Logging
            if (loggingAi) {
                aiLogger.logStatus(currentAiEnv)
            }
            // Manuelles Logging
            if (loggingManual && !manualEnv.isNullOrEmpty()) {
                manualLogger.logStatus(manualEnv)
            }
        }
    }

    private fun checkMismatch(aiEnv: String) {
        val manualEnv = PrefsHelper.getManualEnv(this) ?: run {
            resetMismatch()
            return
        }
        val manualCategory = ServicePrefsHelper.getManualEnvCategory(this) ?: run {
            resetMismatch()
            return
        }

        val aiCatForMismatch = if (aiEnv.lowercase() == "in car") "inside" else aiEnv.lowercase()
        val manCatForMismatch = manualCategory.lowercase()

        if (aiCatForMismatch != "inside" && aiCatForMismatch != "outside") {
            resetMismatch()
            return
        }

        if (aiCatForMismatch != manCatForMismatch) {
            if (mismatchCount == 0) {
                lastMismatchStartTime = System.currentTimeMillis()
            }
            mismatchCount++
            val now = System.currentTimeMillis()
            if (!mismatchNotificationSent && now - lastMismatchStartTime > longMismatchDuration) {
                mismatchNotificationSent = true
                showMismatchNotification(
                    "Long mismatch :(",
                    "The manual status has differed from the AI status for some time!"
                )
            }
        } else {
            if (mismatchCount > 0 && mismatchNotificationSent) {
                showMismatchNotification(
                    "Mismatch fixed :)",
                    "Huhuu! The status again corresponds to the expected environment."
                )
            }
            resetMismatch()
        }
    }

    private fun resetMismatch() {
        mismatchCount = 0
        mismatchNotificationSent = false
    }

    private fun showMismatchNotification(title: String, message: String) {
        val channelId = "mismatch_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Mismatch Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                    lightColor = Color.RED
                }
                nm.createNotificationChannel(channel)
            }
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setVibrate(longArrayOf(0, 500, 500, 500))
        }

        nm.notify((0..9999).random(), builder.build())

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cameraProvider?.unbindAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, ForegroundCameraService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundCameraService::class.java)
            context.stopService(intent)
        }
    }
}

class DummySurfaceProvider(private val context: Context) : Preview.SurfaceProvider {
    override fun onSurfaceRequested(request: SurfaceRequest) {
        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.setDefaultBufferSize(640, 480)
        val surface = Surface(surfaceTexture)

        request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
            surface.release()
            surfaceTexture.release()
        }
    }
}
