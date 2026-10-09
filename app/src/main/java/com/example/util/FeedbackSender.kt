package com.example.util

import java.net.HttpURLConnection
import java.net.URL

/** Posts a payload to the same FormSubmit inbox the website forms use. Returns true only when the service answers success=true (an HTTP 200 alone is not enough). */
object FeedbackSender {
    private const val ENDPOINT = "https://formsubmit.co/ajax/prayagideepak@gmail.com"

    fun send(payload: FeedbackPayload): Boolean {
        return try {
            val json = org.json.JSONObject()
            payload.fields().forEach { (k, v) -> json.put(k, v) }
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            // FormSubmit rejects posts without an Origin. Use the website origin it was activated for.
            conn.setRequestProperty("Origin", "https://prayagi-store-and-services.github.io")
            conn.setRequestProperty("Referer", "https://prayagi-store-and-services.github.io/")
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299 && accepted(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** FormSubmit answers {"success":"true"} only when it really queued the email. */
    fun accepted(body: String): Boolean = Regex("\"success\"\\s*:\\s*\"?true\"?", RegexOption.IGNORE_CASE).containsMatchIn(body)
}
