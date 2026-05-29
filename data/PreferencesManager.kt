package com.securityshield.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * PreferencesManager: All app config stored in EncryptedSharedPreferences.
 *
 * Dependencies (build.gradle.kts):
 * implementation("androidx.security:security-crypto:1.1.0-alpha06")
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_FILE = "security_shield_secure_prefs"
        private const val KEY_SERVICE_ENABLED    = "service_enabled"
        private const val KEY_FAIL_THRESHOLD     = "fail_threshold"
        private const val KEY_FAIL_COUNT         = "current_fail_count"
        private const val KEY_AI_REPORTING       = "ai_reporting_enabled"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID   = "telegram_chat_id"
        private const val KEY_GEMINI_API_KEY     = "gemini_api_key"
        private const val KEY_FACE_ENROLLED      = "owner_face_enrolled"
        private const val KEY_SCREEN_RECORDING   = "screen_recording_enabled"
        private const val DEFAULT_THRESHOLD      = 5
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Service State ──────────────────────────────────────────────────────────

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isAiReportingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_REPORTING, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_REPORTING, value).apply()

    var isScreenRecordingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_RECORDING, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_RECORDING, value).apply()

    var isFaceEnrolled: Boolean
        get() = prefs.getBoolean(KEY_FACE_ENROLLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FACE_ENROLLED, value).apply()

    // ── Thresholds ─────────────────────────────────────────────────────────────

    var failThreshold: Int
        get() = prefs.getInt(KEY_FAIL_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_FAIL_THRESHOLD, value.coerceIn(1, 20)).apply()

    // ── Fail Counter (thread-safe increment) ──────────────────────────────────

    fun incrementFailCount(): Int {
        val newCount = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        prefs.edit().putInt(KEY_FAIL_COUNT, newCount).apply()
        return newCount
    }

    fun resetFailCount() {
        prefs.edit().putInt(KEY_FAIL_COUNT, 0).apply()
    }

    val currentFailCount: Int
        get() = prefs.getInt(KEY_FAIL_COUNT, 0)

    // ── API Credentials ────────────────────────────────────────────────────────

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    // ── Validation ─────────────────────────────────────────────────────────────

    val isTelegramConfigured: Boolean
        get() = telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()

    val isGeminiConfigured: Boolean
        get() = geminiApiKey.isNotBlank()
}
