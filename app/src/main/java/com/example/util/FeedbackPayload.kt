package com.example.util

/**
 * The complete, exact content of an in-app feedback or crash report.
 * Only these fields are ever sent: phone model, Android version, app version, and either the
 * message the user typed (feedback) or the technical stack trace (crash report). Nothing else.
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
        append("- Phone model: ").append(deviceModel).append('\n')
        append("- Android version: ").append(androidVersion).append('\n')
        append("- App version: ").append(appVersion).append('\n')
        if (!message.isNullOrBlank()) append("- Your message: ").append(message).append('\n')
        if (!stackTrace.isNullOrBlank()) append("- Crash stack trace (code locations only):\n").append(stackTrace.take(600)).append('\n')
        append("Nothing else is sent: no name, email, location, files, device IDs or battery history.")
    }
}

object FeedbackBuilder {
    const val MAX_MESSAGE = 1000
    const val MAX_TRACE = 3000
    const val PRIVACY_URL = "https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/privacy.html"

    /** Exception class names and code locations only. Exception messages are dropped on purpose. */
    fun sanitizeStackTrace(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            sb.append(if (depth == 0) "" else "Caused by: ").append(cur.javaClass.name).append('\n')
            for (frame in cur.stackTrace) sb.append("  at ").append(frame.toString()).append('\n')
            cur = cur.cause
            depth++
        }
        return sb.toString().take(MAX_TRACE)
    }

    fun feedback(model: String, androidVersion: String, appVersion: String, message: String): FeedbackPayload =
        FeedbackPayload("Feedback", model, androidVersion, appVersion, message.trim().take(MAX_MESSAGE), null)

    fun crash(model: String, androidVersion: String, appVersion: String, sanitizedTrace: String): FeedbackPayload =
        FeedbackPayload("Crash report", model, androidVersion, appVersion, null, sanitizedTrace.take(MAX_TRACE))
}
