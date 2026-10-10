package com.example.service

import android.content.Context
import android.os.Build
import com.example.NetraApplication
import com.example.model.CrashReport
import java.io.File
import java.util.UUID

class CrashStabilitySentinel(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            persistCrashReport(t, e)
        } catch (ignored: Exception) {
            // Must not fail during crash handling
        }
        
        defaultHandler?.uncaughtException(t, e)
    }

    private fun persistCrashReport(t: Thread, e: Throwable) {
        val app = NetraApplication.instance
        val state = app.centralDataCenter.centralState.value
        
        val report = CrashReport(
            reportId = UUID.randomUUID().toString(),
            reportType = "CRASH",
            timestamp = System.currentTimeMillis(),
            appVersionName = com.example.BuildConfig.VERSION_NAME,
            appVersionCode = com.example.BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            androidApiLevel = Build.VERSION.SDK_INT,
            affectedComponent = "UNKNOWN",
            thread = t.name,
            exceptionClass = e.javaClass.name,
            sanitizedMessage = e.message?.take(500) ?: "No message",
            sanitizedStackTrace = e.stackTraceToString().take(2000),
            causeChain = e.cause?.toString()?.take(500) ?: "None",
            centralUnitState = state.toString().take(1000),
            capabilityState = state.capabilities.toString().take(500)
        )
        
        // Stack trace only (class names and code locations, no exception messages), kept so the user can
        // choose to send it from Settings. Nothing is sent automatically.
        runCatching {
            com.example.util.FeedbackBuilder.stamp(com.example.BuildConfig.VERSION_NAME, com.example.util.FeedbackBuilder.sanitizeStackTrace(e)).let { t -> File(context.filesDir, "pending_crash_report.txt").writeText(t); File(context.filesDir, "last_crash_report.txt").writeText(t) }
        }

        val file = File(context.filesDir, "crash_${report.reportId}.json")
        file.writeText(report.toString())
    }
}
