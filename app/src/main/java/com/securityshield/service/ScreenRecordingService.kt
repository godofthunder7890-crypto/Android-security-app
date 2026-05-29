package com.securityshield.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.securityshield.R
import java.io.File

class ScreenRecordingService : Service() {

    companion object {
        const val TAG = "ScreenRecording"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_OUTPUT_DIR = "output_dir"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "recording_channel"

        // 20-second atomic chunks
        const val RECORDING_DURATION_MS = 20_000L

        // Resolution optimized for thermal safety
        const val RECORD_WIDTH = 640
        const val RECORD_HEIGHT = 480
        const val RECORD_BITRATE = 1_000_000 // 1 Mbps
        const val RECORD_FPS = 15 // frame-skip for thermal safety
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            ?: return START_NOT_STICKY
        val outputDir = intent.getStringExtra(EXTRA_OUTPUT_DIR) ?: filesDir.absolutePath
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: System.currentTimeMillis().toString()

        outputFile = File(outputDir, "screen_$timestamp.mp4")

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        startRecording()

        // Auto-stop after 20 seconds (atomic chunk)
        stopHandler.postDelayed({ stopRecording() }, RECORDING_DURATION_MS)

        return START_NOT_STICKY
    }

    private fun startRecording() {
        try {
            val metrics = getDisplayMetrics()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(RECORD_WIDTH, RECORD_HEIGHT)
                setVideoFrameRate(RECORD_FPS)
                setVideoEncodingBitRate(RECORD_BITRATE)
                setOutputFile(outputFile?.absolutePath)
                prepare()
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "SecurityShield_Capture",
                RECORD_WIDTH, RECORD_HEIGHT, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null, null
            )

            mediaRecorder?.start()
            Log.d(TAG, "Recording started → ${outputFile?.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Recording start failed: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            virtualDisplay?.release()
            mediaProjection?.stop()
            Log.d(TAG, "Recording saved → ${outputFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Recording stop error: ${e.message}", e)
        } finally {
            stopSelf()
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
        }
        return metrics
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Shield")
            .setContentText("Recording incident evidence (20s)")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopHandler.removeCallbacksAndMessages(null)
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
