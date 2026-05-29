package com.securityshield.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.securityshield.R
import com.securityshield.admin.SecurityAdminReceiver
import com.securityshield.databinding.ActivityMainBinding
import com.securityshield.service.SecurityMonitorService
import com.securityshield.utils.PrefsManager
import com.securityshield.worker.AnalysisWorkerScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    companion object {
        const val REQUEST_ADMIN = 100
        const val REQUEST_SCREEN_CAPTURE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, SecurityAdminReceiver::class.java)

        setupUI()
        loadSettings()

        // Schedule daily cleanup
        AnalysisWorkerScheduler.schedulePeriodicCleanup(this)
    }

    private fun setupUI() {
        // Shield master toggle
        binding.switchShieldEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.setShieldEnabled(isChecked)
            if (isChecked) {
                ensureAdminEnabled()
                startMonitoringService()
            } else {
                stopMonitoringService()
            }
            updateStatusCard(isChecked)
        }

        // Fail threshold slider (1-20)
        binding.sliderFailThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val threshold = value.toInt()
                prefs.setFailThreshold(threshold)
                binding.tvThresholdValue.text = "$threshold attempts"
            }
        }

        // AI Reporting toggle
        binding.switchAiReporting.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAiReportingEnabled(isChecked)
        }

        // Intruder Alert toggle
        binding.switchIntruderAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.setIntruderAlertEnabled(isChecked)
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        // View Logs button
        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(this, IncidentLogActivity::class.java))
        }

        // Test trigger button (debug)
        binding.btnTestTrigger.setOnClickListener {
            testTrigger()
        }
    }

    private fun loadSettings() {
        binding.switchShieldEnabled.isChecked = prefs.isShieldEnabled()
        binding.sliderFailThreshold.value = prefs.getFailThreshold().toFloat()
        binding.tvThresholdValue.text = "${prefs.getFailThreshold()} attempts"
        binding.switchAiReporting.isChecked = prefs.isAiReportingEnabled()
        binding.switchIntruderAlert.isChecked = prefs.isIntruderAlertEnabled()
        updateStatusCard(prefs.isShieldEnabled())
    }

    private fun ensureAdminEnabled() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Security Shield needs Device Admin to detect failed unlock attempts."
                )
            }
            startActivityForResult(intent, REQUEST_ADMIN)
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_START_MONITORING
        }
        startForegroundService(intent)
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_STOP_MONITORING
        }
        startService(intent)
    }

    private fun testTrigger() {
        Toast.makeText(this, "Test trigger sent!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, SecurityMonitorService::class.java).apply {
            action = SecurityMonitorService.ACTION_TRIGGER_INTRUDER
            putExtra(SecurityMonitorService.EXTRA_TRIGGER_REASON, "MANUAL_TEST")
            putExtra(SecurityMonitorService.EXTRA_FAIL_COUNT, 0)
        }
        startForegroundService(intent)
    }

    private fun updateStatusCard(active: Boolean) {
        binding.tvShieldStatus.text = if (active) "🛡 Shield ACTIVE" else "🔴 Shield INACTIVE"
        binding.cardStatus.setCardBackgroundColor(
            if (active) getColor(R.color.glass_green) else getColor(R.color.glass_red)
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN) {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Device Admin activated ✓", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchShieldEnabled.isChecked = false
                Toast.makeText(this, "Device Admin required for password monitoring", Toast.LENGTH_LONG).show()
            }
        }
    }
}
