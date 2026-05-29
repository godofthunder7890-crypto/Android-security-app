package com.securityshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.securityshield.prefs.EncryptedPrefsManager
import com.securityshield.service.SecurityService

class SecurityAdminReceiver : DeviceAdminReceiver() {
    companion object { private const val TAG = "SecurityAdmin" }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        val failCount = getManager(context).currentFailedPasswordAttempts
        val prefs = EncryptedPrefsManager(context)
        val threshold = prefs.getInt(EncryptedPrefsManager.KEY_THRESHOLD, 3)
        Log.d(TAG, "Password failed. Count: $failCount / Threshold: $threshold")
        if (failCount >= threshold) {
            SecurityService.triggerIntruderAlert(context, "PASSWORD_FAILED_ATTEMPTS_$failCount")
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        EncryptedPrefsManager(context).putInt(EncryptedPrefsManager.KEY_FAIL_COUNT, 0)
        Log.d(TAG, "Password succeeded — counter reset")
    }

    override fun onEnabled(context: Context, intent: Intent) { Log.i(TAG, "Device Admin ENABLED") }
    override fun onDisabled(context: Context, intent: Intent) { Log.w(TAG, "Device Admin DISABLED") }
}
