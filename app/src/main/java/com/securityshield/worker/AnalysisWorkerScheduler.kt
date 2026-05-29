package com.securityshield.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AnalysisWorkerScheduler {

    fun schedule(
        context: Context,
        incidentDir: String,
        photoPath: String?,
        triggerReason: String,
        failCount: Int,
        latitude: Double?,
        longitude: Double?,
        timestamp: String
    ) {
        val inputData = workDataOf(
            AnalysisWorker.KEY_INCIDENT_DIR to incidentDir,
            AnalysisWorker.KEY_PHOTO_PATH to (photoPath ?: ""),
            AnalysisWorker.KEY_TRIGGER_REASON to triggerReason,
            AnalysisWorker.KEY_FAIL_COUNT to failCount,
            AnalysisWorker.KEY_LATITUDE to (latitude ?: 0.0),
            AnalysisWorker.KEY_LONGITUDE to (longitude ?: 0.0),
            AnalysisWorker.KEY_TIMESTAMP to timestamp
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val analysisWork = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "analysis_$timestamp",
                ExistingWorkPolicy.REPLACE,
                analysisWork
            )
    }

    fun schedulePeriodicCleanup(context: Context) {
        val cleanupWork = PeriodicWorkRequestBuilder<LogCleanupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "log_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )
    }
}
