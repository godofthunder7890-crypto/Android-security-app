package com.securityshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.securityshield.R
import com.securityshield.camera.CameraCapture
import com.securityshield.recorder.ScreenRecordService
import com.securityshield.worker.TelegramUploadWorker
import com.securityshield.ui.DashboardActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class SecurityService : Service() {

    companion object {
        private const val TAG = "SecurityService"
        const val CHANNEL_ID = "security_shield_channel"
        const val NOTIF_ID = 1001
        const val ACTION_TRIGGER = "com.securityshield.TRIGGER"
        const val EXTRA_TRIGGER_REASON = "trigger_reason"

        fun start(context: Context) {
            val intent = Intent(context, SecurityService::class.java)
            context.startForegroundService(intent)
        }

        fun triggerIntruderAlert(context: Context, reason: String) {
            val intent = Intent(context, SecurityService::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(EXTRA_TRIGGER_REASON, reason)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        Log.i(TAG, "SecurityService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER) {
            val reason = intent.getStringExtra(EXTRA_TRIGGER_REASON) ?: "UNKNOWN"
            Log.w(TAG, "Intruder alert triggered: $reason")
            handleIntruderAlert(reason)
        }
        return START_STICKY
    }

    private fun handleIntruderAlert(reason: String) {
        // 1. Capture front camera photo (Android indicator will show — transparent)
        CameraCapture.capture(this) { photoPath ->
            Log.i(TAG, "Photo captured: $photoPath")
            // 2. Schedule Telegram upload via WorkManager
            scheduleTelegramUpload(photoPath, reason)
        }
        // 3. Start screen recording
        ScreenRecordService.startRecording(this)
    }

    private fun scheduleTelegramUpload(photoPath: String, reason: String) {
        val data = workDataOf(
            TelegramUploadWorker.KEY_PHOTO_PATH to photoPath,
            TelegramUploadWorker.KEY_REASON to reason
        )
        val uploadWork = OneTimeWorkRequestBuilder<TelegramUploadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueue(uploadWork)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Shield",
            // Standard LOW — visible but not intrusive. NOT hidden (IMPORTANCE_NONE)
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Security monitoring active"
        }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Security Shield Active")
        .setContentText("Monitoring for unauthorized access")
        .setSmallIcon(R.drawable.ic_shield)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0,
                Intent(this, DashboardActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
