package com.securityshield.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedPrefsManager(context: Context) {
    companion object {
        private const val PREFS_FILE = "security_shield_prefs"
        const val KEY_THRESHOLD       = "fail_threshold"
        const val KEY_FAIL_COUNT      = "fail_count"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_AI_ENABLED      = "ai_enabled"
        const val KEY_TELEGRAM_TOKEN  = "telegram_bot_token"
        const val KEY_TELEGRAM_CHAT   = "telegram_chat_id"
        const val KEY_GEMINI_KEY      = "gemini_api_key"
        const val KEY_OWNER_FACE_PATH = "owner_face_path"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private val prefs = EncryptedSharedPreferences.create(
        context, PREFS_FILE, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getString(key: String, default: String = "") = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getInt(key: String, default: Int = 0) = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}
