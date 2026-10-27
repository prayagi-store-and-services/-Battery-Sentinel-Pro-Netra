package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.NetraApplication
import com.example.util.HeatSuspects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thermal Guardian controller: the Android side of [ThermalGuardianPolicy].
 *
 * What it really does when the phone gets hot (and only then):
 *  - dims the screen through Settings.System, only if the user already granted
 *    "Modify system settings" (the same switch the Saver uses); the previous
 *    brightness is remembered once and restored only if the Guardian changed it;
 *  - cuts its own heavy work: usage-stats queries run only inside the short
 *    identify window, and log writes happen only at phase changes;
 *  - names apps recently in the foreground (Usage Access) as suspects - an
 *    estimate, never per-app heat, because Android reports none;
 *  - controls below critical heat act silently. At critical heat the policy's bounded
 *    warning reaches notifications and the existing AnnouncementEngine; TEST can speak
 *    at other levels too. Every control is recorded in the thermal log;
 *  - lifts every control it applied once the phone stays below 37 C for a minute.
 *
 * What it never does: force-stop other apps, change CPU frequency, toggle Battery
 * Saver, override the manufacturer's thermal safety, or claim a guaranteed cooldown.
 * Charging and discharging are both handled; while charging it also says so, because
 * a hot phone charges slower by design.
 */
class ThermalGuardianController(private val app: Context) {

    data class UiState(
        val enabled: Boolean = false,
        val zone: ThermalGuardianPolicy.Zone = ThermalGuardianPolicy.Zone.COOL,
        val phase: ThermalGuardianPolicy.Phase = ThermalGuardianPolicy.Phase.IDLE,
        val controlsApplied: Boolean = false,
        val lastTempC: Float? = null,
        val charging: Boolean? = null,
        val lastTestAtMs: Long = 0L
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val policy = ThermalGuardianPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var sessionOpen = false
    private var testEvents = mutableListOf<String>()

    // remembered screen state, restored only if WE changed it
    private var savedBrightness: Int? = null
    private var savedBrightnessMode: Int? = null
    private var brightnessDimmed = false

    @Volatile private var thermalStatus: Int = -1
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val tempC = if (t == Int.MIN_VALUE || t <= 0) null else t / 10.0f
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            lastTempC = tempC
            lastCharging = charging
        }
    }

    @Volatile private var lastTempC: Float? = null
    @Volatile private var lastCharging: Boolean? = null
    @Volatile private var lastValidReadAtMs: Long = 0L

    fun start() {
        if (loopJob != null) return
        _ui.value = _ui.value.copy(enabled = true)
        ThermalGuardianLog.init(app)
        try {
            app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val listener = PowerManager.OnThermalStatusChangedListener { status -> thermalStatus = status }
                pm?.addThermalStatusListener(listener)
                thermalListener = listener
            } catch (_: Exception) { thermalStatus = -1 }
        }
        loopJob = scope.launch {
            var gapMs = ThermalGuardianPolicy.GAP_COOL_MS
            while (true) {
                try {
                    tick()
                    gapMs = lastGapMs
                } catch (_: Exception) {
                    gapMs = ThermalGuardianPolicy.GAP_COOL_MS
                }
                delay(gapMs)
            }
        }
    }

    fun stop() {
        loopJob?.cancel(); loopJob = null
        try { app.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
                thermalListener?.let { pm?.removeThermalStatusListener(it) }
            } catch (_: Exception) {}
        }
        restoreBrightnessIfOurs()
        _ui.value = _ui.value.copy(enabled = false, controlsApplied = false)
    }

    @Volatile private var lastGapMs: Long = ThermalGuardianPolicy.GAP_COOL_MS

    private fun tick() {
        // A fresh sticky read every tick: EXTRA_TEMPERATURE is a snapshot, the cadence is ours.
        val snapshot = try { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
        if (snapshot != null) {
            val t = snapshot.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (t != Int.MIN_VALUE && t > 0) {
                lastTempC = t / 10.0f
                lastValidReadAtMs = System.currentTimeMillis()
            }
            val plugged = snapshot.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = snapshot.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            lastCharging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        // A reading older than STALE_READ_MS is not a reading: safety decisions never reuse it.
        val fresh = System.currentTimeMillis() - lastValidReadAtMs <= STALE_READ_MS
        val tempC = if (fresh) lastTempC else null
        val charging = lastCharging
        val ts = if (thermalStatus >= 0) thermalStatus else null
        val directives = policy.onSample(System.currentTimeMillis(), tempC, charging, ts)
        execute(directives, tempC, charging, test = false)
        val st = policy.status()
        _ui.value = _ui.value.copy(
            zone = st.zone, phase = st.phase, controlsApplied = st.controlsApplied,
            lastTempC = tempC, charging = charging
        )
    }

    /** TEST button path: real readings, real spoken line, real log entry - flagged as a test, never dims. */
    fun runTest() {
        val tempC = lastTempC
        val charging = lastCharging
        testEvents = mutableListOf()
        val directives = policy.runTest(System.currentTimeMillis(), tempC, charging, if (thermalStatus >= 0) thermalStatus else null)
        execute(directives, tempC, charging, test = true)
        ThermalGuardianLog.recordTest(testEvents.toList(), tempC, charging)
        _ui.value = _ui.value.copy(lastTestAtMs = System.currentTimeMillis())
    }

    private fun execute(directives: List<ThermalGuardianPolicy.Directive>, tempC: Float?, charging: Boolean?, test: Boolean) {
        var gap = ThermalGuardianPolicy.GAP_COOL_MS
        for (d in directives) {
            when (d) {
                is ThermalGuardianPolicy.Directive.SampleGap -> gap = d.gapMs
                is ThermalGuardianPolicy.Directive.Speak -> dispatchWarning(d, test, policy.status().zone, ::speak, ::postWarning)
                is ThermalGuardianPolicy.Directive.LogEvent -> {
                    if (test) testEvents.add(d.event) else {
                        ThermalGuardianLog.addEvent(d.event)
                        if (d.event == "light_controls_on" || d.event == "active_controls_on" || d.event == "severe_controls_on") {
                            if (!sessionOpen) {
                                sessionOpen = true
                                ThermalGuardianLog.beginSession(System.currentTimeMillis(), tempC ?: 0f, charging)
                            }
                        }
                        if (d.event == "recovered" || d.event == "session_closed_cool") {
                            if (sessionOpen) {
                                sessionOpen = false
                                ThermalGuardianLog.endSession(System.currentTimeMillis(), tempC, reachedSafeBand = d.event == "recovered")
                            }
                        }
                    }
                }
                is ThermalGuardianPolicy.Directive.ApplyLightControls -> if (!test) dimScreen(LIGHT_BRIGHTNESS)
                is ThermalGuardianPolicy.Directive.ApplyActiveControls -> if (!test) dimScreen(ACTIVE_BRIGHTNESS)
                is ThermalGuardianPolicy.Directive.ApplySevereControls -> if (!test) dimScreen(ACTIVE_BRIGHTNESS)
                is ThermalGuardianPolicy.Directive.RestoreControls -> if (!test) restoreBrightnessIfOurs()
                is ThermalGuardianPolicy.Directive.IdentifyHeavyApps -> identifyHeavyApps(test)
                is ThermalGuardianPolicy.Directive.Notify -> dispatchWarning(d, test, policy.status().zone, ::speak, ::postWarning)
                is ThermalGuardianPolicy.Directive.ClearNotification -> clearWarning()
            }
        }
        lastGapMs = gap
        if (!test && tempC != null) ThermalGuardianLog.trackMax(tempC)
    }

    private fun speak(text: String, critical: Boolean) {
        try {
            NetraApplication.instance.announcementEngine.enqueue(
                AnnouncementItem(
                    id = "thermal_guardian_${System.currentTimeMillis()}",
                    text = text,
                    priority = if (critical) AnnouncementPriority.CRITICAL_THERMAL else AnnouncementPriority.INFORMATIONAL,
                    category = "THERMAL_GUARDIAN",
                    isNightException = critical
                )
            )
        } catch (_: Exception) {}
    }

    private fun dimScreen(target: Int) {
        brightnessDimmed = SavingSettings.display(app, "thermal", target)
        ThermalGuardianLog.addEvent(if (brightnessDimmed) "brightness_dimmed_to_$target" else "brightness_skipped_consent_permission_or_owner")
    }

    private fun restoreBrightnessIfOurs() {
        if (SavingSettings.restoreDisplay(app, "thermal")) brightnessDimmed = false
    }

    private fun identifyHeavyApps(test: Boolean) {
        val suspects = try { HeatSuspects.find(app, lastTempC, requireHot = !test) } catch (_: Exception) { emptyList() }
        if (suspects.isEmpty()) {
            if (!test) ThermalGuardianLog.addEvent("heavy_apps_none_or_no_usage_access")
            if (test) testEvents.add("heavy_apps_none_or_no_usage_access")
            return
        }
        val names = suspects.joinToString(", ") { it.appName }
        if (test) {
            testEvents.add("heavy_apps: $names")
        } else {
            ThermalGuardianLog.addEvent("heavy_apps: $names")
            if (ThermalSilentPolicy.mayAnnounce(false)) speak("Recently used while hot: $names. Closing them helps.", critical = false)
        }
    }

    private fun postWarning(title: String, body: String, severe: Boolean) {
        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Thermal Guardian",
                    if (severe) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(ch)
            }
            val openApp = PendingIntent.getActivity(
                app, 0, app.packageManager.getLaunchIntentForPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
            nm.notify(NOTIF_ID, n)
        } catch (_: Exception) {}
    }

    private fun clearWarning() {
        try {
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    companion object {
        /** The real controller output boundary, shared by live execution and regression tests. */
        internal fun dispatchWarning(
            directive: ThermalGuardianPolicy.Directive,
            userTest: Boolean,
            zone: ThermalGuardianPolicy.Zone,
            speak: (String, Boolean) -> Unit,
            notify: (String, String, Boolean) -> Unit
        ) {
            if (!ThermalSilentPolicy.mayAnnounce(userTest, zone == ThermalGuardianPolicy.Zone.CRITICAL)) return
            when (directive) {
                is ThermalGuardianPolicy.Directive.Speak -> speak(directive.text, directive.critical)
                is ThermalGuardianPolicy.Directive.Notify -> notify(directive.title, directive.body, directive.severe)
                else -> Unit
            }
        }

        private const val CHANNEL_ID = "thermal_guardian_channel"
        private const val NOTIF_ID = 42042
        private const val LIGHT_BRIGHTNESS = 102   // ~40%
        private const val ACTIVE_BRIGHTNESS = 64   // ~25%
        private const val STALE_READ_MS = 120_000L // a battery read older than this is treated as unavailable
    }
}
