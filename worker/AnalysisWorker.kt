package com.securityshield.worker

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Base64
import android.util.Log
import androidx.work.*
import com.securityshield.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AnalysisWorker: WorkManager coroutine worker that:
 * 1. Extracts 3 key frames from the recorded MP4
 * 2. Sends frames to Gemini 1.5 Flash for Hinglish analysis
 * 3. Reports analysis + media files to Telegram Bot API
 *
 * WorkManager ensures this runs even if the app is backgrounded/killed.
 */
class AnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG           = "AnalysisWorker"
        const val KEY_VIDEO_PATH        = "video_path"
        const val KEY_AI_ENABLED        = "ai_enabled"

        private const val GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private const val TELEGRAM_BASE  = "https://api.telegram.org/bot"
    }

    private val prefs = PreferencesManager(applicationContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoPath = inputData.getString(KEY_VIDEO_PATH)
        val aiEnabled = inputData.getBoolean(KEY_AI_ENABLED, true)

        if (videoPath.isNullOrBlank()) {
            Log.e(TAG, "No video path provided.")
            return@withContext Result.failure()
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file not found: $videoPath")
            return@withContext Result.failure()
        }

        try {
            Log.i(TAG, "Starting analysis for ${videoFile.name}")

            // Step 1: Extract 3 frames from the video
            val frames = extractKeyFrames(videoFile, count = 3)
            if (frames.isEmpty()) {
                Log.e(TAG, "Frame extraction failed.")
                return@withContext Result.retry()
            }

            // Step 2: AI analysis (optional)
            val aiReport = if (aiEnabled && prefs.isGeminiConfigured) {
                analyzeWithGemini(frames, prefs.geminiApiKey)
            } else {
                buildFallbackReport(videoFile)
            }

            // Step 3: Send report to Telegram
            if (prefs.isTelegramConfigured) {
                sendTelegramTextReport(aiReport)
                sendTelegramVideo(videoFile)
                // Send the first extracted frame as a photo
                frames.firstOrNull()?.let { sendTelegramPhoto(it, videoFile.name) }
            }

            // Step 4: Cleanup old logs (>7 days)
            cleanupOldLogs()

            Log.i(TAG, "Analysis complete.")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    // ── Frame Extraction ───────────────────────────────────────────────────────

    private fun extractKeyFrames(videoFile: File, count: Int): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 20_000L

            val intervalUs = (durationMs * 1000L) / (count + 1)

            for (i in 1..count) {
                val timeUs = intervalUs * i
                val bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (bitmap != null) {
                    // Downscale for API efficiency
                    val scaled = Bitmap.createScaledBitmap(bitmap, 640, 480, true)
                    frames.add(scaled)
                }
            }
        } finally {
            retriever.release()
        }
        Log.i(TAG, "Extracted ${frames.size} frames from ${videoFile.name}")
        return frames
    }

    // ── Gemini 1.5 Flash Analysis ──────────────────────────────────────────────

    private fun analyzeWithGemini(frames: List<Bitmap>, apiKey: String): String {
        val partsArray = JSONArray()

        // Add Hinglish analysis prompt
        partsArray.put(JSONObject().put("text",
            """
            Tu ek security AI hai. In phone screen recordings ke frames ko dekh aur bata:
            1. Kya koi suspicious activity dikh rahi hai?
            2. Screen par kya content visible hai?
            3. Koi unauthorized access ke signs hain?
            4. Overall risk level: LOW / MEDIUM / HIGH
            
            Apna analysis HINGLISH mein de (Hindi + English mix).
            Concise reh — max 5-6 lines.
            """.trimIndent()
        ))

        // Add each frame as base64 inline data
        frames.forEach { bitmap ->
            val base64 = bitmapToBase64(bitmap)
            partsArray.put(
                JSONObject().put("inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64)
                )
            )
        }

        val requestBody = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", partsArray)
            ))
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", 300)
                .put("temperature", 0.4)
            )
            .toString()

        val request = Request.Builder()
            .url("$GEMINI_API_URL?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
            return buildFallbackReport(null)
        }

        val json = JSONObject(responseBody)
        return json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun buildFallbackReport(videoFile: File?): String {
        val timestamp = java.text.SimpleDateFormat(
            "dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date())
        return """
            🔴 SECURITY SHIELD ALERT
            
            📅 Time: $timestamp
            📁 File: ${videoFile?.name ?: "N/A"}
            ⚠️ Trigger: Unauthorized access attempt detected
            
            AI analysis unavailable (API not configured).
            Evidence files attached below.
        """.trimIndent()
    }

    // ── Telegram Reporting ─────────────────────────────────────────────────────

    private fun sendTelegramTextReport(reportText: String) {
        val url = "${TELEGRAM_BASE}${prefs.telegramBotToken}/sendMessage"
        val body = JSONObject()
            .put("chat_id", prefs.telegramChatId)
            .put("text", "🛡️ *Security Shield Report*\n\n$reportText")
            .put("parse_mode", "Markdown")
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        Log.i(TAG, "Telegram text response: ${response.code}")
    }

    private fun sendTelegramVideo(videoFile: File) {
        val url = "${TELEGRAM_BASE}${prefs.telegramBotToken}/sendVideo"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", prefs.telegramChatId)
            .addFormDataPart(
                "video", videoFile.name,
                videoFile.asRequestBody("video/mp4".toMediaType())
            )
            .addFormDataPart("caption", "📹 Screen recording evidence")
            .build()

        val request = Request.Builder().url(url).post(body).build()
        val response = httpClient.newCall(request).execute()
        Log.i(TAG, "Telegram video response: ${response.code}")
    }

    private fun sendTelegramPhoto(bitmap: Bitmap, filename: String) {
        val url = "${TELEGRAM_BASE}${prefs.telegramBotToken}/sendPhoto"
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val photoBytes = baos.toByteArray()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", prefs.telegramChatId)
            .addFormDataPart(
                "photo", "$filename.jpg",
                photoBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .addFormDataPart("caption", "📸 Key frame from evidence")
            .build()

        val request = Request.Builder().url(url).post(body).build()
        val response = httpClient.newCall(request).execute()
        Log.i(TAG, "Telegram photo response: ${response.code}")
    }

    // ── Log Cleanup (7 days) ───────────────────────────────────────────────────

    private fun cleanupOldLogs() {
        val logDir = File(applicationContext.filesDir, "security_logs")
        if (!logDir.exists()) return

        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        logDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
                deletedCount++
            }
        }
        if (deletedCount > 0) Log.i(TAG, "Cleaned up $deletedCount old log files.")
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}

// ── Scheduler ─────────────────────────────────────────────────────────────────

object AnalysisWorkerScheduler {

    fun schedule(context: Context, videoPath: String, isAiEnabled: Boolean) {
        val inputData = workDataOf(
            AnalysisWorker.KEY_VIDEO_PATH to videoPath,
            AnalysisWorker.KEY_AI_ENABLED to isAiEnabled
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("security_analysis")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "analysis_${System.currentTimeMillis()}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
        Log.i("AnalysisWorkerScheduler", "Analysis job queued for $videoPath")
    }
}
