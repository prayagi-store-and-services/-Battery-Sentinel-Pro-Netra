package com.example.service

import java.net.URI

internal object StabilityDiagnosticPolicy {
    fun isSecureEndpoint(endpoint: String): Boolean = try {
        val uri = URI(endpoint)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.fragment == null && uri.query == null && (uri.port == -1 || uri.port == 443)
    } catch (_: Exception) { false }

    /** No messages, file paths, thread names or cause text can enter a transmitted stack. */
    fun safeStack(error: Throwable): String = error.stackTrace.asSequence()
        .filter { it.className.startsWith("com.example.") }
        .take(40)
        .joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        .take(8000)
}
