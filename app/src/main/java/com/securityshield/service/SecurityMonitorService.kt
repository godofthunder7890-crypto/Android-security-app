package com.securityshield.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.thermal.ThermalStatus
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.securityshield.R
import com.securityshield.camera.FrontCameraCapture
import com.securityshield.reporting.TelegramReporter
import com.securityshield.ui.MainActivity
import com.securityshield.utils.PrefsManager
import com.securityshield.worker.AnalysisWorkerScheduler
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SecurityMonitorService : Service() {

    companion object {
        const val TAG = "SecurityMonitor"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_TRIGGER_INTRUDER = "ACTION_TRIGGER_INTRUDER"
        const val ACTION_STOP_MONITORING = "ACTION_STOP_MONITORING"
        const val EXTRA_TRIGGER_REASON = "trigger_reason"
        const val EXTRA_FAIL_COUNT = "fail_count"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "security_shield_channel"

        // Thermal threshold (Celsius)
        const val THERMAL_PAUSE_THRESHOLD = 42
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPipelineRunning = false
    private lateinit var prefs: PrefsManager
    private lateinit var cameraCapture: FrontCameraCapture
    private var thermalManager: PowerManager? = null

    // Auto-stop handler after 2min screen-off inactivity
    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        Log.d(TAG, "Auto-stop: 2 min inactivity, stopping recording")
        stopRecordingService()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        cameraCapture = FrontCameraCapture(this)
        thermalManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Security Shield Active"))

        when (intent?.action) {
            ACTION_START_MONITORING -> {
                Log.d(TAG, "Monitoring started")
            }
            ACTION_TRIGGER_INTRUDER -> {
                val reason = intent.getStringExtra(EXTRA_TRIGGER_REASON) ?: "UNKNOWN"
                val failCount = intent.getIntExtra(EXTRA_FAIL_COUNT, 0)
                Log.d(TAG, "Intruder trigger: $reason, fails: $failCount")
                if (!isPipelineRunning) {
                    executeSecurityPipeline(reason, failCount)
                }
            }
            ACTION_STOP_MONITORING -> stopSelf()
        }

        return START_STICKY
    }

    private fun executeSecurityPipeline(reason: String, failCount: Int) {
        if (isThermallyThrottled()) {
            Log.w(TAG, "Device too hot — skipping recording to prevent overheating")
            notifyThermalPause()
            return
        }

        isPipelineRunning = true
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val incidentDir = createIncidentDirectory(timestamp)

        serviceScope.launch {
            try {
                // Action 1: Front camera capture (visible Android green dot indicator)
                Log.d(TAG, "Capturing intruder photo...")
                val photoFile = cameraCapture.capturePhoto(incidentDir, timestamp)

                // Action 2: Get location
                val location = getLastKnownLocation()

                // Action 3: Trigger screen recording (20s atomic chunk)
                triggerScreenRecording(incidentDir, timestamp)

                // Action 4: Schedule AI analysis + Telegram report via WorkManager
                AnalysisWorkerScheduler.schedule(
                    context = this@SecurityMonitorService,
                    incidentDir = incidentDir.absolutePath,
                    photoPath = photoFile?.absolutePath,
                    triggerReason = reason,
                    failCount = failCount,
                    latitude = location?.first,
                    longitude = location?.second,
                    timestamp = timestamp
                )

                // Start auto-stop timer (2 minutes)
                autoStopHandler.postDelayed(autoStopRunnable, 2 * 60 * 1000L)

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error: ${e.message}", e)
                isPipelineRunning = false
            }
        }
    }

    private fun triggerScreenRecording(incidentDir: File, timestamp: String) {
        val intent = Intent(this, ScreenRecordingService::class.java).apply {
            putExtra(ScreenRecordingService.EXTRA_OUTPUT_DIR, incidentDir.absolutePath)
            putExtra(ScreenRecordingService.EXTRA_TIMESTAMP, timestamp)
        }
        startForegroundService(intent)
    }

    private fun stopRecordingService() {
        stopService(Intent(this, ScreenRecordingService::class.java))
        isPipelineRunning = false
    }

    private fun createIncidentDirectory(timestamp: String): File {
        val dir = File(filesDir, "security_logs/incident_$timestamp")
        dir.mkdirs()
        return dir
    }

    private suspend fun getLastKnownLocation(): Pair<Double, Double>? {
        return try {
            // Returns last cached location (no foreground permission needed for last known)
            null // Placeholder — implement with FusedLocationProviderClient
        } catch (e: Exception) { null }
    }

    private fun isThermallyThrottled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        } else false
    }

    private fun notifyThermalPause() {
        updateNotification("Shield paused — device cooling down")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Shield",
                // Standard importance — visible to user as required
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Security Shield monitoring service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Shield")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStopHandler.removeCallbacks(autoStopRunnable)
        serviceScope.cancel()
        cameraCapture.shutdown()
    }

    override fun onBind(intent: Intent?) = null
}
