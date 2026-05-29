package com.securityshield

import android.app.Application
import android.util.Log

class SecurityShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("SecurityShield", "App initialized — Realme GT 6T / Android 16")
    }
}
