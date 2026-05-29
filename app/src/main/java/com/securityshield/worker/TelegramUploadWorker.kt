package com.securityshield.worker

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.securityshield.prefs.EncryptedPrefsManager
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TelegramUploadWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "TelegramWorker"
        const val KEY_PHOTO_PATH = "photo_path"
        const val KEY_REASON     = "reason"
        const val KEY_AI_REPORT  = "ai_report"
        private const val API    = "https://api.telegram.org/bot"
    }

    private val prefs = EncryptedPrefsManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result {
        val photoPath = inputData.getString(KEY_PHOTO_PATH)
        val reason    = inputData.getString(KEY_REASON) ?: "Unknown"
        val aiReport  = inputData.getString(KEY_AI_REPORT) ?: ""
        val token     = prefs.getString(EncryptedPrefsManager.KEY_TELEGRAM_TOKEN)
        val chatId    = prefs.getString(EncryptedPrefsManager.KEY_TELEGRAM_CHAT)

        if (token.isBlank() || chatId.isBlank()) { Log.w(TAG, "Telegram not configured"); return Result.failure() }

        return try {
            val loc = getLocation()
            val time = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date())
            val msg = buildString {
                appendLine("🚨 *Security Shield Alert*")
                appendLine("━━━━━━━━━━━━━━━━━━━━")
                appendLine("📅 *Time:* $time")
                appendLine("⚠️ *Trigger:* $reason")
                loc?.let {
                    appendLine("📍 *Location:* ${it.latitude}, ${it.longitude}")
                    appendLine("🗺 [Maps](https://maps.google.com/?q=${it.latitude},${it.longitude})")
                }
                if (aiReport.isNotBlank()) { appendLine("━━━━━━━━━━━━━━━━━━━━"); appendLine("🤖 *AI:* $aiReport") }
            }
            sendText(token, chatId, msg)
            photoPath?.let { File(it).takeIf { f -> f.exists() }?.let { f -> sendPhoto(token, chatId, f) } }
            Result.success()
        } catch (e: Exception) { Log.e(TAG, "Upload failed", e); Result.retry() }
    }

    private fun sendText(token: String, chatId: String, text: String) {
        val body = JSONObject().apply { put("chat_id", chatId); put("text", text); put("parse_mode", "Markdown") }
            .toString().toRequestBody("application/json".toMediaTypeOrNull())
        client.newCall(Request.Builder().url("$API$token/sendMessage").post(body).build()).execute().close()
    }

    private fun sendPhoto(token: String, chatId: String, file: File) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", "📸 Intruder captured")
            .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()
        client.newCall(Request.Builder().url("$API$token/sendPhoto").post(body).build()).execute().close()
    }

    private suspend fun getLocation(): Location? = try {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
    } catch (e: Exception) { null }
}
