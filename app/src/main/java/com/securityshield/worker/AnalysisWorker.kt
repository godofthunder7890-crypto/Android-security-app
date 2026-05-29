package com.securityshield.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.securityshield.reporting.TelegramReporter
import com.securityshield.utils.PrefsManager
import java.io.File

class AnalysisWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "AnalysisWorker"
        const val KEY_INCIDENT_DIR = "incident_dir"
        const val KEY_PHOTO_PATH = "photo_path"
        const val KEY_TRIGGER_REASON = "trigger_reason"
        const val KEY_FAIL_COUNT = "fail_count"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_TIMESTAMP = "timestamp"
    }

    override suspend fun doWork(): Result {
        val prefs = PrefsManager(context)
        val incidentDir = inputData.getString(KEY_INCIDENT_DIR) ?: return Result.failure()
        val photoPath = inputData.getString(KEY_PHOTO_PATH)
        val triggerReason = inputData.getString(KEY_TRIGGER_REASON) ?: "UNKNOWN"
        val failCount = inputData.getInt(KEY_FAIL_COUNT, 0)
        val latitude = inputData.getDouble(KEY_LATITUDE, 0.0)
        val longitude = inputData.getDouble(KEY_LONGITUDE, 0.0)
        val timestamp = inputData.getString(KEY_TIMESTAMP) ?: ""

        return try {
            var aiReport = ""

            // Step 1: Gemini AI Analysis (if enabled)
            if (prefs.isAiReportingEnabled() && photoPath?.isNotEmpty() == true) {
                aiReport = runGeminiAnalysis(
                    photoPath = photoPath,
                    incidentDir = incidentDir,
                    triggerReason = triggerReason,
                    failCount = failCount,
                    prefs = prefs
                )
            }

            // Step 2: Telegram Report
            val reporter = TelegramReporter(
                botToken = prefs.getTelegramBotToken(),
                chatId = prefs.getTelegramChatId()
            )

            reporter.sendIncidentReport(
                photoPath = photoPath,
                videoPath = findVideoFile(incidentDir),
                aiReport = aiReport,
                triggerReason = triggerReason,
                failCount = failCount,
                latitude = latitude,
                longitude = longitude,
                timestamp = timestamp
            )

            Log.d(TAG, "Incident report sent successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Analysis worker failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun runGeminiAnalysis(
        photoPath: String,
        incidentDir: String,
        triggerReason: String,
        failCount: Int,
        prefs: PrefsManager
    ): String {
        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = prefs.getGeminiApiKey()
            )

            // Load intruder photo as bitmap
            val bitmap = BitmapFactory.decodeFile(photoPath)
                ?: return "Photo analysis unavailable"

            // Extract 3 frames from video if available
            val frames = extractVideoFrames(incidentDir)

            val prompt = buildGeminiPrompt(triggerReason, failCount)

            val response = model.generateContent(
                content {
                    image(bitmap)
                    frames.forEach { image(it) }
                    text(prompt)
                }
            )

            response.text ?: "Analysis completed — no text response"

        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis failed: ${e.message}")
            "AI analysis unavailable: ${e.message}"
        }
    }

    private fun buildGeminiPrompt(triggerReason: String, failCount: Int): String {
        return """
            Ye ek security incident report hai. Mujhe Hinglish mein ek short analysis do.
            
            Trigger: $triggerReason
            Failed attempts: $failCount
            
            Bata:
            1. Photo mein koi face visible hai?
            2. Screen recording se kya activity dikh rahi hai?
            3. Ye authorized user lagta hai ya unauthorized?
            4. Risk level: Low/Medium/High
            
            Short aur clear report do, 5-6 lines mein.
        """.trimIndent()
    }

    private fun extractVideoFrames(incidentDir: String): List<Bitmap> {
        // Extract 3 frames from first video file in incident dir
        val videoFile = findVideoFile(incidentDir) ?: return emptyList()
        val frames = mutableListOf<Bitmap>()
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(videoFile)
            val duration = retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: 20000L
            // Extract at 5s, 10s, 15s
            listOf(5000L, 10000L, 15000L).forEach { timeMs ->
                if (timeMs < duration) {
                    retriever.getFrameAtTime(timeMs * 1000)?.let { frames.add(it) }
                }
            }
            retriever.release()
            frames
        } catch (e: Exception) {
            Log.w(TAG, "Frame extraction failed: ${e.message}")
            emptyList()
        }
    }

    private fun findVideoFile(incidentDir: String): String? {
        return File(incidentDir).listFiles()
            ?.firstOrNull { it.extension == "mp4" }
            ?.absolutePath
    }
}
