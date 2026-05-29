package com.securityshield.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.securityshield.databinding.ActivitySetupBinding
import com.securityshield.utils.PrefsManager

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        loadCurrentValues()

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadCurrentValues() {
        binding.etTelegramToken.setText(prefs.getTelegramBotToken())
        binding.etTelegramChatId.setText(prefs.getTelegramChatId())
        binding.etGeminiKey.setText(prefs.getGeminiApiKey())
    }

    private fun saveSettings() {
        val token = binding.etTelegramToken.text.toString().trim()
        val chatId = binding.etTelegramChatId.text.toString().trim()
        val geminiKey = binding.etGeminiKey.text.toString().trim()

        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Telegram token aur Chat ID required hai", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.setTelegramBotToken(token)
        prefs.setTelegramChatId(chatId)
        prefs.setGeminiApiKey(geminiKey)

        Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
        finish()
    }
}
