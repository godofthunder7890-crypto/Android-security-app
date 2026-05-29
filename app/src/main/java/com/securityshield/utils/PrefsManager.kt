package com.securityshield.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PrefsManager(context: Context) {

    companion object {
        const val PREFS_NAME = "security_shield_prefs"
        const val KEY_SHIELD_ENABLED = "shield_enabled"
        const val KEY_FAIL_THRESHOLD = "fail_threshold"
        const val KEY_FAIL_COUNT = "fail_count"
        const val KEY_AI_REPORTING = "ai_reporting"
        const val KEY_INTRUDER_ALERT = "intruder_alert"
        const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_MEDIAPROJECTION_CODE = "mediaprojection_code"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isShieldEnabled() = prefs.getBoolean(KEY_SHIELD_ENABLED, false)
    fun setShieldEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHIELD_ENABLED, enabled).apply()

    fun getFailThreshold() = prefs.getInt(KEY_FAIL_THRESHOLD, 5)
    fun setFailThreshold(threshold: Int) = prefs.edit().putInt(KEY_FAIL_THRESHOLD, threshold).apply()

    fun getFailCount() = prefs.getInt(KEY_FAIL_COUNT, 0)
    fun incrementFailCount() = prefs.edit().putInt(KEY_FAIL_COUNT, getFailCount() + 1).apply()
    fun resetFailCount() = prefs.edit().putInt(KEY_FAIL_COUNT, 0).apply()

    fun isAiReportingEnabled() = prefs.getBoolean(KEY_AI_REPORTING, true)
    fun setAiReportingEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AI_REPORTING, enabled).apply()

    fun isIntruderAlertEnabled() = prefs.getBoolean(KEY_INTRUDER_ALERT, true)
    fun setIntruderAlertEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_INTRUDER_ALERT, enabled).apply()

    fun getTelegramBotToken() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "") ?: ""
    fun setTelegramBotToken(token: String) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, token).apply()

    fun getTelegramChatId() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
    fun setTelegramChatId(chatId: String) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, chatId).apply()

    fun getGeminiApiKey() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    fun setGeminiApiKey(key: String) = prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()

    fun saveMediaProjectionCode(resultCode: Int) =
        prefs.edit().putInt(KEY_MEDIAPROJECTION_CODE, resultCode).apply()
    fun getMediaProjectionCode() = prefs.getInt(KEY_MEDIAPROJECTION_CODE, -1)
}
