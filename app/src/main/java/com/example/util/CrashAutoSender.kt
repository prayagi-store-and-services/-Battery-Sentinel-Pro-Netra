package com.example.util

import android.content.Context
import java.io.File

/**
 * File names for the locally saved crash report. Capture is automatic (a sanitized trace is written on crash);
 * SENDING IS NEVER AUTOMATIC - the user reviews the report in the feedback card and presses Send.
 * The report is exactly what FeedbackBuilder.crash builds: phone model, Android version, app version and the sanitized
 * stack trace (class names and code locations only, exception messages dropped).
 */
object CrashAutoSender {
    const val PENDING_FILE = "pending_crash_report.txt"
    const val LAST_FILE = "last_crash_report.txt"
}
