package com.securityshield.reporting

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class TelegramReporter(
    private val botToken: String,
    private val chatId: String
) {
    companion object {
        const val TAG = "TelegramReporter"
        const val BASE_URL = "https://api.telegram.org/bot"
    }

    private val client = OkHttpClient()

    suspend fun sendIncidentReport(
        photoPath: String?,
        videoPath: String?,
        aiReport: String,
        triggerReason: String,
        failCount: Int,
        latitude: Double,
        longitude: Double,
        timestamp: String
    ) = withContext(Dispatchers.IO) {
        if (botToken.isEmpty() || chatId.isEmpty()) {
            Log.w(TAG, "Telegram not configured — skipping report")
            return@withContext
        }

        // 1. Send text report
        val message = buildReportMessage(triggerReason, failCount, aiReport, latitude, longitude, timestamp)
        sendMessage(message)

        // 2. Send photo if available
        photoPath?.let {
            val file = File(it)
            if (file.exists()) sendPhoto(file, "📸 Intruder captured at $timestamp")
        }

        // 3. Send video if available
        videoPath?.let {
            val file = File(it)
            if (file.exists()) sendVideo(file, "🎥 Screen recording — $timestamp")
        }

        // 4. Send location if available
        if (latitude != 0.0 && longitude != 0.0) {
            sendLocation(latitude, longitude)
        }
    }

    private fun buildReportMessage(
        triggerReason: String,
        failCount: Int,
        aiReport: String,
        latitude: Double,
        longitude: Double,
        timestamp: String
    ): String {
        return """
🚨 *SECURITY ALERT — Security Shield*

⏰ *Time:* $timestamp
🔴 *Trigger:* $triggerReason
🔢 *Failed Attempts:* $failCount
📍 *Location:* ${if (latitude != 0.0) "$latitude, $longitude" else "Unavailable"}

🤖 *AI Analysis:*
$aiReport

---
_Security Shield — Transparent Device Protection_
        """.trimIndent()
    }

    private fun sendMessage(text: String) {
        try {
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL$botToken/sendMessage")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Message sent: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}")
        }
    }

    private fun sendPhoto(file: File, caption: String) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "photo", file.name,
                    file.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("$BASE_URL$botToken/sendPhoto")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Photo sent: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendPhoto failed: ${e.message}")
        }
    }

    private fun sendVideo(file: File, caption: String) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "video", file.name,
                    file.asRequestBody("video/mp4".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("$BASE_URL$botToken/sendVideo")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Video sent: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendVideo failed: ${e.message}")
        }
    }

    private fun sendLocation(lat: Double, lon: Double) {
        try {
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("latitude", lat)
                put("longitude", lon)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL$botToken/sendLocation")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Location sent: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendLocation failed: ${e.message}")
        }
    }
}
