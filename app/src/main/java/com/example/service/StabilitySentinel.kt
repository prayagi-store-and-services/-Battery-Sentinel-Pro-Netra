package com.example.service

import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.NetraApplication
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/**
 * Crash + runtime stability sentinel.
 *
 * Crash handling is deliberately local-only on the crashing thread: the report is persisted
 * first and network delivery is deferred until the next healthy process/worker opportunity.
 * No runtime permission is requested for telemetry delivery beyond the existing INTERNET
 * permission. The remote endpoint is optional and must be configured outside source control.
 */
class StabilitySentinel(private val app: NetraApplication) {

    companion object {
        private const val TAG = "NetraStability"
        private const val ROOT_DIR = "stability"
        private const val PENDING_DIR = "pending"
        private const val HEALTHY_DIR = "healthy"
        private const val PREFS = "netra_stability"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val MAX_STACK_CHARS = 16_000
        private const val MAX_PENDING_REPORTS = 20
        private const val MAX_HEALTHY_REPORTS = 48
        private const val MIN_STARTUP_GRACE_MS = 5L * 60L * 1000L

        fun installCrashHandler(app: NetraApplication): StabilitySentinel {
            val sentinel = StabilitySentinel(app)
            sentinel.installUncaughtExceptionHandler()
            return sentinel
        }
    }

    private val context = app.applicationContext
    private val root = File(context.filesDir, ROOT_DIR)
    private val pending = File(root, PENDING_DIR)
    private val healthy = File(root, HEALTHY_DIR)
    private val prefs = context.getSharedPreferences(PREFS, 0)
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        root.mkdirs()
        pending.mkdirs()
        healthy.mkdirs()
    }

    private fun installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistCrash(thread, throwable)
            } catch (_: Throwable) {
                // Never allow diagnostics to interfere with Android's crash handling.
            }
            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {
                // Keep the platform crash path intact even if an old handler fails.
            }
        }
    }

    private fun persistCrash(thread: Thread, throwable: Throwable) {
        val report = baseReport("CRASH", "runtime")
            .put("thread", thread.name.take(120))
            .put("exceptionClass", throwable.javaClass.name.take(300))
            .put("exceptionMessage", sanitize(throwable.message).take(1000))
            .put("stackTrace", stackTrace(throwable))
            .put("causeChain", causeChain(throwable))
            .put("centralState", centralStateSummary())

        val target = File(pending, "crash-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        target.writeText(report.toString(), Charsets.UTF_8)
        trimDirectory(pending, MAX_PENDING_REPORTS)
    }

    suspend fun flushPendingReports() = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.STABILITY_REPORT_URL.trim()
        if (endpoint.isEmpty()) return@withContext

        pending.listFiles()?.sortedBy { it.lastModified() }?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".json")) return@forEach
            try {
                val accepted = StabilityReportTransport.post(endpoint, file.readText(Charsets.UTF_8))
                if (accepted) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Deferred stability report delivery failed: ${e.javaClass.simpleName}")
                return@forEach
            }
        }
    }

    suspend fun runHealthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val telemetry = app.telemetrySentinel.healthReport.value
        val state = app.centralDataCenter.centralState.value
        val capability = state.capabilities[CapabilityType.BATTERY_TELEMETRY]
        val uptimeHealthyEnough = System.currentTimeMillis() - app.startedAtMillis >= MIN_STARTUP_GRACE_MS

        val issue = when {
            uptimeHealthyEnough && telemetry.state == TelemetryHealthState.STALE -> "Battery telemetry stale"
            uptimeHealthyEnough && telemetry.state == TelemetryHealthState.FAILED -> "Telemetry sentinel failed"
            uptimeHealthyEnough && capability == CapabilityStatus.UNAVAILABLE -> "Battery telemetry capability unavailable"
            else -> null
        }

        val result = HealthCheckResult(
            healthy = issue == null,
            issue = issue,
            telemetryState = telemetry.state.name
        )

        val report = baseReport(
            type = if (result.healthy) "HEALTH" else "INCIDENT",
            component = if (result.healthy) "runtime" else "telemetry"
        )
            .put("healthy", result.healthy)
            .put("issue", result.issue)
            .put("telemetryState", result.telemetryState)
            .put("centralState", centralStateSummary())
            .put("capabilities", JSONObject().apply {
                state.capabilities.forEach { (key, value) -> put(key.name, value.name) }
            })

        if (result.healthy) {
            val file = File(healthy, "health-${System.currentTimeMillis()}.json")
            file.writeText(report.toString(), Charsets.UTF_8)
            trimDirectory(healthy, MAX_HEALTHY_REPORTS)
        } else {
            val fingerprint = issueFingerprint(report)
            val file = File(pending, "incident-$fingerprint-${System.currentTimeMillis()}.json")
            file.writeText(report.toString(), Charsets.UTF_8)
            trimDirectory(pending, MAX_PENDING_REPORTS)
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        result
    }

    fun lastCheckTimestamp(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    private fun baseReport(type: String, component: String): JSONObject = JSONObject()
        .put("reportId", UUID.randomUUID().toString())
        .put("reportType", type)
        .put("component", component)
        .put("timestamp", System.currentTimeMillis())
        .put("appVersionCode", BuildConfig.VERSION_CODE)
        .put("appVersionName", BuildConfig.VERSION_NAME)
        .put("deviceManufacturer", Build.MANUFACTURER)
        .put("deviceModel", Build.MODEL)
        .put("androidVersion", Build.VERSION.RELEASE ?: "unknown")
        .put("androidSdk", Build.VERSION.SDK_INT)

    private fun centralStateSummary(): JSONObject {
        val state = app.centralDataCenter.centralState.value
        return JSONObject()
            .put("batteryLevel", state.batteryLevel)
            .put("chargerState", state.canonicalChargerState.name)
            .put("temperatureC", state.temperatureCelsius)
            .put("voltageMv", state.voltageMv)
            .put("currentMa", state.currentMa)
            .put("powerWatts", state.powerWatts)
            .put("chargingSpeed", state.chargingSpeed.name)
            .put("dataFresh", state.isDataFresh)
            .put("lastUpdateTimestamp", state.lastUpdateTimestamp)
    }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString().take(MAX_STACK_CHARS)
    }

    private fun causeChain(throwable: Throwable): String {
        val names = mutableListOf<String>()
        var current: Throwable? = throwable
        while (current != null && names.size < 8) {
            names += current.javaClass.name.take(200)
            current = current.cause
        }
        return names.joinToString(" -> ")
    }

    private fun sanitize(value: String?): String = value.orEmpty()
        .replace(
            Regex("(?i)(token|password|secret|authorization|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+"),
            "$1=<redacted>"
        )

    private fun issueFingerprint(report: JSONObject): String =
        listOf(
            report.optString("reportType"),
            report.optString("component"),
            report.optString("issue"),
            report.optString("exceptionClass"),
            report.optInt("androidSdk"),
            report.optString("deviceModel"),
            report.optInt("appVersionCode")
        ).joinToString("|").hashCode().toUInt().toString(16)

    private fun trimDirectory(dir: File, maxFiles: Int) {
        dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(maxFiles)?.forEach { it.delete() }
    }

    data class HealthCheckResult(val healthy: Boolean, val issue: String?, val telemetryState: String)
}

internal object StabilityReportTransport {
    fun post(endpoint: String, payload: String): Boolean {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Battery-Sentinel-Pro-Netra/${BuildConfig.VERSION_NAME}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }
}
