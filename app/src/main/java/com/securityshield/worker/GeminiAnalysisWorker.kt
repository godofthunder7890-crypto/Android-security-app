package com.securityshield.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.securityshield.prefs.EncryptedPrefsManager
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiAnalysisWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "GeminiWorker"
        const val KEY_VIDEO_PATH = "video_path"
        const val KEY_PHOTO_PATH = "photo_path"
    }

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString(KEY_VIDEO_PATH)
        val photoPath = inputData.getString(KEY_PHOTO_PATH)
        val prefs = EncryptedPrefsManager(context)
        val apiKey = prefs.getString(EncryptedPrefsManager.KEY_GEMINI_KEY)

        if (apiKey.isBlank()) { Log.w(TAG, "Gemini key not set"); return Result.failure() }
        if (!prefs.getBoolean(EncryptedPrefsManager.KEY_AI_ENABLED, true)) return Result.success()

        return try {
            val frames = when {
                videoPath != null -> extractFrames(videoPath)
                photoPath != null -> listOfNotNull(BitmapFactory.decodeFile(photoPath))
                else -> emptyList()
            }
            if (frames.isEmpty()) return Result.failure()

            val report = analyzeWithGemini(apiKey, frames)
            Log.i(TAG, "Gemini report: $report")

            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<TelegramUploadWorker>()
                    .setInputData(workDataOf(
                        TelegramUploadWorker.KEY_AI_REPORT to report,
                        TelegramUploadWorker.KEY_REASON to "AI_ANALYSIS",
                        TelegramUploadWorker.KEY_PHOTO_PATH to (photoPath ?: "")
                    ))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
            Result.success()
        } catch (e: Exception) { Log.e(TAG, "Gemini error", e); Result.retry() }
    }

    private fun extractFrames(videoPath: String): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(videoPath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 20_000L
            listOf(0.2, 0.5, 0.8).forEach { pct ->
                retriever.getFrameAtTime((duration * pct * 1000).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST)?.let { frames.add(it) }
            }
        } finally { retriever.release() }
        return frames
    }

    private suspend fun analyzeWithGemini(apiKey: String, frames: List<Bitmap>): String {
        val model = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = apiKey)
        val prompt = """
            Yeh ek security incident hai. In frames ko dekho aur Hinglish mein concise security report do:
            1. Koi insaan dikh raha hai? Face visible?
            2. Device access karne ki koshish?
            3. Environment (indoor/outdoor, lighting)
            4. Risk level: LOW / MEDIUM / HIGH
            Max 150 words mein.
        """.trimIndent()
        return model.generateContent(content { frames.forEach { image(it) }; text(prompt) }).text
            ?: "Analysis unavailable"
    }
}
