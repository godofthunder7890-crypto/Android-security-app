package com.securityshield.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.securityshield.R
import com.securityshield.admin.SecurityAdminReceiver
import com.securityshield.prefs.EncryptedPrefsManager
import com.securityshield.service.SecurityService
import com.securityshield.worker.LogCleanupWorker

class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    companion object { private const val REQUEST_ADMIN = 1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        prefs = EncryptedPrefsManager(this)
        dpm   = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, SecurityAdminReceiver::class.java)

        setupUI()
        LogCleanupWorker.schedule(this)
    }

    private fun setupUI() {
        // Threshold slider (1–20)
        val slider = findViewById<SeekBar>(R.id.sliderThreshold)
        val tvThreshold = findViewById<TextView>(R.id.tvThreshold)
        slider.max = 19
        slider.progress = prefs.getInt(EncryptedPrefsManager.KEY_THRESHOLD, 3) - 1
        tvThreshold.text = "Failed Attempts Threshold: ${slider.progress + 1}"
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val value = p + 1
                tvThreshold.text = "Failed Attempts Threshold: $value"
                prefs.putInt(EncryptedPrefsManager.KEY_THRESHOLD, value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // AI toggle
        val switchAI = findViewById<Switch>(R.id.switchAI)
        switchAI.isChecked = prefs.getBoolean(EncryptedPrefsManager.KEY_AI_ENABLED, true)
        switchAI.setOnCheckedChangeListener { _, checked ->
            prefs.putBoolean(EncryptedPrefsManager.KEY_AI_ENABLED, checked)
        }

        // Service toggle
        val switchService = findViewById<Switch>(R.id.switchService)
        switchService.isChecked = prefs.getBoolean(EncryptedPrefsManager.KEY_SERVICE_ENABLED, false)
        switchService.setOnCheckedChangeListener { _, checked ->
            prefs.putBoolean(EncryptedPrefsManager.KEY_SERVICE_ENABLED, checked)
            if (checked) {
                ensureDeviceAdmin()
                SecurityService.start(this)
            } else stopService(Intent(this, SecurityService::class.java))
        }

        // Setup / Owner registration
        findViewById<Button>(R.id.btnSetup).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        // View logs
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, IncidentLogActivity::class.java))
        }

        // Settings (Telegram / Gemini keys)
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun ensureDeviceAdmin() {
        if (!dpm.isAdminActive(adminComponent)) {
            startActivityForResult(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Required to detect failed unlock attempts")
                }, REQUEST_ADMIN
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN && resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Device Admin enabled ✓", Toast.LENGTH_SHORT).show()
        }
    }
}
