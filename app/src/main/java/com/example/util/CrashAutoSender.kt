package com.example.util

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import java.io.File

/**
 * Sends the crash report saved by the last crash, automatically, the next time the app starts.
 * The report is exactly what FeedbackBuilder.crash builds: phone model, Android version, app version and the sanitized
 * stack trace (class names and code locations only, exception messages dropped). The file is deleted only after a
 * successful send, so a failed send is retried at the next start.
 */
object CrashAutoSender {
    const val PENDING_FILE = "pending_crash_report.txt"
    const val LAST_FILE = "last_crash_report.txt"

    fun sendPending(context: Context): Boolean {
        val file = File(context.filesDir, PENDING_FILE)
        if (!file.exists()) return false
        val trace = runCatching { file.readText() }.getOrDefault("")
        if (trace.isBlank()) { runCatching { file.delete() }; return false }
        val payload = FeedbackBuilder.crash(Build.MODEL ?: "Unknown", Build.VERSION.RELEASE ?: "Unknown", BuildConfig.VERSION_NAME, trace)
        val ok = FeedbackSender.send(payload)
        if (ok) runCatching { File(context.filesDir, LAST_FILE).writeText(trace); file.delete() }
        return ok
    }
}
