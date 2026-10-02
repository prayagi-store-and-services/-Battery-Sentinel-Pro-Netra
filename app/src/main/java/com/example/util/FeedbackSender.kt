package com.example.util

import java.net.HttpURLConnection
import java.net.URL

/** Posts a payload to the same FormSubmit inbox the website forms use. Returns true only on an HTTP 2xx. */
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
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }
}
