package com.example.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object StabilityHealthScheduler {
    private const val UNIQUE_WORK = "netra-stability-hourly-health"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<StabilityHealthWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
