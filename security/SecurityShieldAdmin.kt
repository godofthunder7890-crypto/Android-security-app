package com.securityshield.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.securityshield.data.PreferencesManager
import com.securityshield.service.SecurityShieldService

/**
 * DeviceAdminReceiver: Listens for system-level device policy events.
 *
 * Registration in AndroidManifest.xml:
 * <receiver android:name=".security.SecurityShieldAdmin"
 *     android:permission="android.permission.BIND_DEVICE_ADMIN">
 *     <meta-data android:name="android.app.device_admin"
 *         android:resource="@xml/device_admin_policies" />
 *     <intent-filter>
 *         <action android:name="android.app.action.ACTION_DEVICE_ADMIN_ENABLED"/>
 *         <action android:name="android.app.action.ACTION_PASSWORD_FAILED"/>
 *         <action android:name="android.app.action.ACTION_PASSWORD_SUCCEEDED"/>
 *     </intent-filter>
 * </receiver>
 *
 * res/xml/device_admin_policies.xml:
 * <device-admin xmlns:android="http://schemas.android.com/apk/res/android">
 *   <uses-policies>
 *     <watch-login />
 *   </uses-policies>
 * </device-admin>
 */
class SecurityShieldAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SecurityShieldAdmin"
        const val EXTRA_FAIL_COUNT = "extra_fail_count"
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        val prefs = PreferencesManager(context)

        if (!prefs.isServiceEnabled) {
            Log.d(TAG, "Service disabled — ignoring password failure.")
            return
        }

        val failCount = prefs.incrementFailCount()
        val threshold = prefs.failThreshold
        Log.i(TAG, "Password failed. Attempts: $failCount / Threshold: $threshold")

        if (failCount >= threshold) {
            Log.w(TAG, "Threshold reached. Triggering security pipeline.")
            triggerSecurityPipeline(context, failCount, TriggerSource.PASSWORD_FAILED)
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        val prefs = PreferencesManager(context)
        Log.i(TAG, "Password succeeded. Resetting fail counter.")
        prefs.resetFailCount()
    }

    private fun triggerSecurityPipeline(
        context: Context,
        failCount: Int,
        source: TriggerSource
    ) {
        val serviceIntent = Intent(context, SecurityShieldService::class.java).apply {
            action = SecurityShieldService.ACTION_TRIGGER_SECURITY
            putExtra(SecurityShieldService.EXTRA_TRIGGER_SOURCE, source.name)
            putExtra(EXTRA_FAIL_COUNT, failCount)
        }
        context.startForegroundService(serviceIntent)
    }
}

enum class TriggerSource {
    PASSWORD_FAILED,
    FACE_MISMATCH,
    MANUAL_TEST
}
