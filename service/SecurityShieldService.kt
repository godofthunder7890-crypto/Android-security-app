package com.securityshield.service

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.securityshield.R
import com.securityshield.data.PreferencesManager
import com.securityshield.media.SilentFrontCameraCapture
import com.securityshield.worker.AnalysisWorkerScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SecurityShieldService: Foreground service that orchestrates the full
 * evidence-collection pipeline on trigger.
 *
 * Flow:
 * 1. Receives ACTION_TRIGGER_SECURITY intent
 * 2. Starts screen recording (MediaProjection, 20s atomic chunk)
 * 3. Captures front-camera photo (Camera2, silent)
 * 4. After 2 min inactivity (screen off) OR 20s limit: stops recording
 * 5. Schedules WorkManager job for AI analysis + Telegram reporting
 *
 * Manifest requirements:
 * <service android:name=".service.SecurityShieldService"
 *     android:foregroundServiceType="mediaProjection|camera"
 *     android:exported="false" />
 *
 * Permissions:
 * FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION,
 * FOREGROUND_SERVICE_CAMERA, RECORD_AUDIO (optional), CAMERA,
 * WAKE_LOCK, RECEIVE_BOOT_COMPLETED
 */
class SecurityShieldService : Service() {

    companion object {
        const val ACTION_TRIGGER_SECURITY  = "action.TRIGGER_SECURITY"
        const val ACTION_STOP_SERVICE      = "action.STOP_SERVICE"
        const val ACTION_MEDIA_PROJECTION  = "action.MEDIA_PROJECTION_RESULT"
        const val EXTRA_TRIGGER_SOURCE     = "extra.TRIGGER_SOURCE"
        const val EXTRA_PROJECTION_RESULT  = "extra.PROJECTION_RESULT"
        const val EXTRA_PROJECTION_DATA    = "extra.PROJECTION_DATA"

        private const val TAG                    = "SecurityShieldService"
        private const val NOTIFICATION_ID        = 1001
        private const val CHANNEL_ID             = "shield_service_channel"
        private const val INACTIVITY_TIMEOUT_MS  = 2 * 60 * 1000L   // 2 minutes
        private const val RECORDING_CHUNK_MS     = 20 * 1000L        // 20 seconds
        private const val VIDEO_WIDTH            = 640
        private const val VIDEO_HEIGHT           = 480
        private const val VIDEO_BITRATE          = 1_500_000
        private const val VIDEO_FPS              = 15
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screenStateReceiver: BroadcastReceiver

    // Auto-stop after 2 min of screen-off inactivity
    private val inactivityStopRunnable = Runnable {
        Log.i(TAG, "Inactivity timeout — stopping recording.")
        stopRecordingAndScheduleAnalysis()
    }

    // Hard-stop after 20-second recording chunk
    private val chunkStopRunnable = Runnable {
        Log.i(TAG, "20s chunk complete — stopping recording.")
        stopRecordingAndScheduleAnalysis()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        acquireWakeLock()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_TRIGGER_SECURITY -> {
                val source = intent.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "UNKNOWN"
                Log.w(TAG, "Security triggered! Source: $source")
                handleSecurityTrigger(source)
            }
            ACTION_MEDIA_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startScreenRecording(resultCode, data)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopRecordingAndScheduleAnalysis()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── Trigger Handler ────────────────────────────────────────────────────────

    private fun handleSecurityTrigger(source: String) {
        // Capture front camera immediately (no MediaProjection permission needed)
        captureFrontCameraPhoto(source)

        // Screen recording requires user-granted MediaProjection permission.
        // If already granted from a previous session stored in service state, proceed.
        // Otherwise, launch the permission-request activity transparently.
        if (prefs.isScreenRecordingEnabled) {
            requestMediaProjectionPermission(source)
        }
    }

    // ── Screen Recording ───────────────────────────────────────────────────────

    private fun requestMediaProjectionPermission(source: String) {
        // Launch a transparent activity to acquire MediaProjection consent.
        // This is REQUIRED by Android — there is no way to screen-record
        // without the user seeing and accepting the system dialog at least once.
        val intent = Intent(this, MediaProjectionPermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_TRIGGER_SOURCE, source)
        }
        startActivity(intent)
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        if (isRecording) {
            Log.d(TAG, "Already recording — ignoring duplicate start.")
            return
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, handler)

        currentRecordingFile = createOutputFile("screen")
        val outputPath = currentRecordingFile!!.absolutePath

        mediaRecorder = MediaRecorder(this).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
            setVideoFrameRate(VIDEO_FPS)
            setVideoEncodingBitRate(VIDEO_BITRATE)
            setOutputFile(outputPath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "SecurityShieldCapture",
            VIDEO_WIDTH, VIDEO_HEIGHT,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface,
            null, handler
        )

        mediaRecorder!!.start()
        isRecording = true
        Log.i(TAG, "Screen recording started → $outputPath")

        // Hard stop after 20s chunk
        handler.postDelayed(chunkStopRunnable, RECORDING_CHUNK_MS)
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system.")
            cleanupRecording()
        }
    }

    // ── Front Camera Capture ───────────────────────────────────────────────────

    private fun captureFrontCameraPhoto(source: String) {
        val outputFile = createOutputFile("selfie", extension = "jpg")
        val capture = SilentFrontCameraCapture(this)
        capture.capture(outputFile) { success ->
            if (success) {
                Log.i(TAG, "Selfie captured → ${outputFile.absolutePath}")
            } else {
                Log.e(TAG, "Selfie capture failed.")
            }
        }
    }

    // ── Stop & Schedule Analysis ───────────────────────────────────────────────

    private fun stopRecordingAndScheduleAnalysis() {
        handler.removeCallbacks(inactivityStopRunnable)
        handler.removeCallbacks(chunkStopRunnable)

        if (!isRecording) return

        val recordedFile = currentRecordingFile
        cleanupRecording()

        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            Log.i(TAG, "Scheduling analysis for ${recordedFile.name}")
            AnalysisWorkerScheduler.schedule(
                context = this,
                videoPath = recordedFile.absolutePath,
                isAiEnabled = prefs.isAiReportingEnabled
            )
        }
    }

    private fun cleanupRecording() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
        isRecording = false
        Log.d(TAG, "Recording resources released.")
    }

    // ── Screen State Receiver (inactivity detection) ───────────────────────────

    private fun registerScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (isRecording) {
                            Log.d(TAG, "Screen off — inactivity timer started.")
                            handler.postDelayed(inactivityStopRunnable, INACTIVITY_TIMEOUT_MS)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen on — cancelling inactivity timer.")
                        handler.removeCallbacks(inactivityStopRunnable)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun createOutputFile(prefix: String, extension: String = "mp4"): File {
        val dir = File(filesDir, "security_logs").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_${timestamp}.$extension")
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecurityShield::RecordingLock")
        wakeLock.acquire(10 * 60 * 1000L) // max 10 min
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Shield",
            // IMPORTANCE_LOW keeps notification present (required by Android)
            // but silent. IMPORTANCE_NONE would suppress the notification
            // entirely, which causes issues on Android 12+.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SecurityShieldService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Shield Active")
            .setContentText("Monitoring device access")
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupRecording()
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        if (wakeLock.isHeld) wakeLock.release()
        Log.i(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
