package com.example.util

/**
 * The complete, exact content of an in-app feedback or crash report.
 * Only these fields are ever sent: the app name (fixed, so every mail says which app sent it),
 * phone model, Android version, app version, and either the message the user typed (feedback)
 * or the technical stack trace (crash report). Nothing else.
 */
data class FeedbackPayload(
    val kind: String,
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
    val message: String?,
    val stackTrace: String?
) {
    /** Form fields posted to the same FormSubmit pipeline as the website forms. */
    fun fields(): Map<String, String> {
        val map = linkedMapOf(
            "_subject" to "[Netra] In-app $kind",
            "_captcha" to "false",
            "_template" to "table",
            "app" to "Battery Sentinel Pro Netra",
            "device" to deviceModel,
            "android_version" to androidVersion,
            "app_version" to appVersion
        )
        if (!message.isNullOrBlank()) map["message"] = message
        if (!stackTrace.isNullOrBlank()) map["stack_trace"] = stackTrace
        return map
    }

    /** Plain-language list shown to the user before they agree to send. */
    fun disclosure(): String = buildString {
        append("This will be emailed to the developer:\n")
        append("- App: Battery Sentinel Pro Netra\n")
        append("- Phone model: ").append(deviceModel).append('\n')
        append("- Android version: ").append(androidVersion).append('\n')
        append("- App version: ").append(appVersion).append('\n')
        if (!message.isNullOrBlank()) append("- Your message: ").append(message).append('\n')
        if (!stackTrace.isNullOrBlank()) append("- Crash stack trace (code locations only):\n").append(stackTrace.take(FeedbackBuilder.MAX_TRACE)).append('\n')
        append("Nothing else is sent: no name, email, location, files, device IDs or battery history.")
    }
}

object FeedbackBuilder {
    const val MAX_MESSAGE = 1000
    const val MAX_TRACE = 6000
    const val PRIVACY_URL = "https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/privacy.html"

    /** Exception class names and code locations only. Exception messages are dropped on purpose. */
    fun sanitizeStackTrace(t: Throwable): String {
        // Wrapper exceptions come first and used to use up the size limit, so the root cause was cut off.
        // Outer levels keep a few frames, the root cause keeps more.
        val chain = ArrayList<Throwable>()
        var cur: Throwable? = t
        while (cur != null && chain.size < 6 && chain.none { it === cur }) { chain.add(cur); cur = cur.cause }
        val sb = StringBuilder()
        chain.forEachIndexed { i, e ->
            val isRoot = i == chain.size - 1
            sb.append(if (i == 0) "" else "Caused by: ").append(e.javaClass.name).append('\n')
            for (frame in e.stackTrace.take(if (isRoot) 25 else 6)) sb.append("  at ").append(frame.toString()).append('\n')
        }
        return sb.toString().take(MAX_TRACE)
    }

    fun feedback(model: String, androidVersion: String, appVersion: String, message: String): FeedbackPayload =
        FeedbackPayload("Feedback", model, androidVersion, appVersion, message.trim().take(MAX_MESSAGE), null)

    const val STAMP_PREFIX = "[captured on app version "
    const val UNKNOWN_LABEL = "[captured on an earlier version, exact version unknown]"
    const val UNKNOWN_VERSION = "unknown (earlier version)"

    /** Written when the crash happens, so the report always says which build actually crashed. */
    fun stamp(version: String, trace: String): String = STAMP_PREFIX + version + "]\n" + trace

    /** The version a saved trace was captured on, or null for a trace saved before stamping existed. */
    fun stampedVersion(trace: String): String? =
        if (trace.startsWith(STAMP_PREFIX)) trace.removePrefix(STAMP_PREFIX).substringBefore(']').ifBlank { null } else null

    /**
     * The app version sent is the one the trace was captured on, never the version that happens to be installed when it is sent.
     * A trace saved before stamping existed is labelled as such. [appVersion] is no longer used for crash reports.
     */
    fun crash(model: String, androidVersion: String, @Suppress("UNUSED_PARAMETER") appVersion: String, sanitizedTrace: String): FeedbackPayload {
        val ver = stampedVersion(sanitizedTrace)
        val body = if (ver != null) sanitizedTrace else UNKNOWN_LABEL + "\n" + sanitizedTrace
        return FeedbackPayload("Crash report", model, androidVersion, ver ?: UNKNOWN_VERSION, null, body.take(MAX_TRACE))
    }
}
