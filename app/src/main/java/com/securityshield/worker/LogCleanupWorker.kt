package com.securityshield.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

class LogCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "LogCleanup"
        private const val RETENTION_DAYS = 7L

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "log_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LogCleanupWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }

    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        var deleted = 0
        listOf("security_captures", "security_recordings").forEach { dir ->
            File(applicationContext.filesDir, dir).listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) { file.delete(); deleted++ }
            }
        }
        Log.i(TAG, "Cleanup done — $deleted files deleted")
        return Result.success()
    }
}
