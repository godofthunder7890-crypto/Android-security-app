package com.securityshield.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.securityshield.R
import com.securityshield.prefs.EncryptedPrefsManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = EncryptedPrefsManager(this)
        val etToken  = findViewById<EditText>(R.id.etTelegramToken)
        val etChatId = findViewById<EditText>(R.id.etTelegramChatId)
        val etGemini = findViewById<EditText>(R.id.etGeminiKey)

        etToken.setText(prefs.getString(EncryptedPrefsManager.KEY_TELEGRAM_TOKEN))
        etChatId.setText(prefs.getString(EncryptedPrefsManager.KEY_TELEGRAM_CHAT))
        etGemini.setText(prefs.getString(EncryptedPrefsManager.KEY_GEMINI_KEY))

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            prefs.putString(EncryptedPrefsManager.KEY_TELEGRAM_TOKEN, etToken.text.toString().trim())
            prefs.putString(EncryptedPrefsManager.KEY_TELEGRAM_CHAT, etChatId.text.toString().trim())
            prefs.putString(EncryptedPrefsManager.KEY_GEMINI_KEY, etGemini.text.toString().trim())
            Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
