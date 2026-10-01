package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.NetraApplication

/** Hourly bounded diagnostic worker. It is not used for live telemetry/UI polling. */
class StabilityHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as NetraApplication
            app.stabilitySentinel.runHealthCheck()
            app.stabilitySentinel.flushPendingReports()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
