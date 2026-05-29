package com.securityshield.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securityshield.prefs.EncryptedPrefsManager
import com.securityshield.service.SecurityService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = EncryptedPrefsManager(context)
            if (prefs.getBoolean(EncryptedPrefsManager.KEY_SERVICE_ENABLED, false)) {
                SecurityService.start(context)
            }
        }
    }
}
