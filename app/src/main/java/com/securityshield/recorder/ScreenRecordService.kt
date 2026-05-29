package com.securityshield.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.securityshield.R
import com.securityshield.worker.GeminiAnalysisWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {
    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record_channel"
        private const val NOTIF_ID = 1002
        private const val DURATION_MS = 20_000L
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA  = "result_data"

        fun startRecording(context: Context, resultCode: Int = -1, data: Intent? = null) {
            context.startForegroundService(Intent(context, ScreenRecordService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                data?.let { putExtra(EXTRA_RESULT_DATA, it) }
            })
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL_ID, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Shield — Recording")
            .setContentText("Evidence capture in progress")
            .setSmallIcon(R.drawable.ic_shield).setOngoing(true).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (rc != -1 && data != null) {
            mediaProjection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(rc, data)
            startCapture()
        } else { Log.w(TAG, "No projection token"); stopSelf() }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val dir = File(filesDir, "security_recordings").apply { mkdirs() }
        outputFile = File(dir, "screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4")

        mediaRecorder = MediaRecorder(this).apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(640, 360)
            setVideoFrameRate(15)
            setVideoEncodingBitRate(1_000_000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
        }

        mediaProjection?.createVirtualDisplay("SecurityCapture", 640, 360,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null)

        mediaRecorder?.start()
        Log.i(TAG, "Recording → ${outputFile?.absolutePath}")
        handler.postDelayed(::stopCapture, DURATION_MS)
    }

    private fun stopCapture() {
        try {
            mediaRecorder?.stop(); mediaRecorder?.release(); mediaProjection?.stop()
            outputFile?.absolutePath?.let { path ->
                WorkManager.getInstance(this).enqueue(
                    OneTimeWorkRequestBuilder<GeminiAnalysisWorker>()
                        .setInputData(workDataOf(GeminiAnalysisWorker.KEY_VIDEO_PATH to path))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
            }
        } catch (e: Exception) { Log.e(TAG, "Stop error", e) } finally { stopSelf() }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
