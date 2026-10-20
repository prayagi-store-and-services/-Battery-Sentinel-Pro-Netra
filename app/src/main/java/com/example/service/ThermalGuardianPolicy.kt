package com.example.service

/**
 * Thermal Guardian: pure decision logic for phone-temperature control.
 *
 * Honest limits, by design: an Android app cannot cool a phone directly and must never
 * claim it can. It can only (a) lower the screen brightness it is allowed to change,
 * (b) cut its own heavy work while the phone is hot, (c) name apps the user recently
 * had open, (d) warn the user, and (e) watch the trend. Manufacturer thermal safety is
 * never overridden, cooling is never guaranteed, and every action the Guardian takes is
 * recorded and lifted again only when it was the Guardian itself that applied it.
 *
 * Thresholds (starter values, not universal): 35-37 monitor, 38-39 light control,
 * 40 active control, 42+ severe. Controls lift only after the phone stays below 37 for
 * a sustained hold, so the screen does not flicker between bright and dim.
 */
class ThermalGuardianPolicy {

    enum class Zone { COOL, MONITOR, LIGHT, ACTIVE, SEVERE }
    enum class Phase { IDLE, OBSERVE, IDENTIFY, TREND, RECOVERING }

    sealed class Directive {
        /** Speak a line to the user. [critical] plays even at night; [test] marks a user-triggered test. */
        data class Speak(val text: String, val critical: Boolean, val test: Boolean = false) : Directive()
        /** Dim the screen to the light level and cut the Guardian's own heavy work. */
        data object ApplyLightControls : Directive()
        /** Dim further, check charging, and name recently used heavy apps. */
        data object ApplyActiveControls : Directive()
        /** Maximum own-load cut and an urgent repeating warning. */
        data object ApplySevereControls : Directive()
        /** Ask the controller to list recently used apps (Usage Access needed). */
        data object IdentifyHeavyApps : Directive()
        /** Lift only the controls the Guardian itself applied. */
        data object RestoreControls : Directive()
        /** The gap the controller should wait before its next battery read. */
        data class SampleGap(val gapMs: Long) : Directive()
        /** Append an event line to the session metrics log. */
        data class LogEvent(val event: String) : Directive()
        /** Post or update the persistent warning notification; null clears it. */
        data class Notify(val title: String, val body: String, val severe: Boolean) : Directive()
        data object ClearNotification : Directive()
    }

    companion object {
        const val MONITOR_C = 35f
        const val LIGHT_C = 38f
        const val ACTIVE_C = 40f
        const val SEVERE_C = 42f
        const val RECOVER_C = 37f
        const val RECOVER_HOLD_MS = 60_000L
        const val OBSERVE_MS = 30_000L            // phase A: read only
        const val IDENTIFY_END_MS = 120_000L      // phase B ends at 2 minutes
        const val TREND_END_MS = 300_000L         // phase C ends at 5 minutes
        const val COOL_END_HOLD_MS = 300_000L     // below 35 for 5 min closes a session
        const val SEVERE_SPEAK_COOLDOWN_MS = 60_000L
        const val ACTIVE_SPEAK_COOLDOWN_MS = 3 * 60_000L
        const val GAP_COOL_MS = 60_000L
        const val GAP_MONITOR_MS = 30_000L
        const val GAP_LIGHT_MS = 15_000L
        const val GAP_ACTIVE_MS = 8_000L          // phase B wants a re-read every 5-10 s
        const val GAP_SEVERE_MS = 5_000L
    }

    var zone: Zone = Zone.COOL
        private set
    var phase: Phase = Phase.IDLE
        private set
    var controlsApplied: Boolean = false
        private set

    private var episodeStartMs: Long = 0L
    private var episodeStartTemp: Float? = null
    private var episodeCharging: Boolean? = null
    private var episodeMaxTemp: Float = 0f
    private var belowRecoverSinceMs: Long? = null
    private var belowCoolSinceMs: Long? = null
    private var lastActiveSpeakMs: Long = 0L
    private var lastSevereSpeakMs: Long = 0L
    private var sensorWarned = false
    private var identifyDone = false

    fun zoneFor(tempC: Float): Zone = when {
        tempC >= SEVERE_C -> Zone.SEVERE
        tempC >= ACTIVE_C -> Zone.ACTIVE
        tempC >= LIGHT_C -> Zone.LIGHT
        tempC >= MONITOR_C -> Zone.MONITOR
        else -> Zone.COOL
    }

    private fun gapFor(z: Zone): Long = when (z) {
        Zone.COOL -> GAP_COOL_MS
        Zone.MONITOR -> GAP_MONITOR_MS
        Zone.LIGHT -> GAP_LIGHT_MS
        Zone.ACTIVE -> GAP_ACTIVE_MS
        Zone.SEVERE -> GAP_SEVERE_MS
    }

    /**
     * Feed one battery read. [thermalStatus] is PowerManager's thermal status (0-7) or null
     * when the phone does not share it; it never overrides the battery temperature, it only
     * confirms severity. Returns the directives for the controller to execute, in order.
     */
    fun onSample(nowMs: Long, tempC: Float?, charging: Boolean?, thermalStatus: Int? = null): List<Directive> {
        val out = ArrayList<Directive>()
        if (tempC == null || tempC <= 0f || tempC > 100f) {
            if (!sensorWarned) {
                sensorWarned = true
                out.add(Directive.LogEvent("sensor_unavailable"))
            }
            out.add(Directive.SampleGap(GAP_COOL_MS))
            return out
        }
        sensorWarned = false

        val newZone = zoneFor(tempC)
        if (tempC > episodeMaxTemp && zone != Zone.COOL) episodeMaxTemp = tempC

        // --- escalation (immediate) ---
        if (newZone.ordinal > zone.ordinal) {
            when (newZone) {
                Zone.LIGHT -> {
                    if (zone == Zone.COOL || zone == Zone.MONITOR) startEpisode(nowMs, tempC, charging)
                    zone = Zone.LIGHT
                    out.add(Directive.ApplyLightControls)
                    out.add(Directive.LogEvent("light_controls_on"))
                    out.add(Directive.Speak("Phone warm, ${fmt(tempC)} degrees. Dimming the screen to help it cool.", critical = false))
                }
                Zone.ACTIVE -> {
                    if (zone == Zone.COOL || zone == Zone.MONITOR) startEpisode(nowMs, tempC, charging)
                    zone = Zone.ACTIVE
                    phase = Phase.OBSERVE
                    episodeStartMs = if (episodeStartMs == 0L) nowMs else episodeStartMs
                    out.add(Directive.ApplyActiveControls)
                    out.add(Directive.LogEvent("active_controls_on"))
                    out.add(
                        Directive.Notify(
                            title = "Phone hot: ${fmt(tempC)}Â°C",
                            body = "Thermal Guardian is dimming the screen and cutting its own work. Close heavy apps; cooling is not guaranteed.",
                            severe = false
                        )
                    )
                    speakActive(out, nowMs, tempC, charging)
                }
                Zone.SEVERE -> {
                    if (zone == Zone.COOL || zone == Zone.MONITOR) startEpisode(nowMs, tempC, charging)
                    zone = Zone.SEVERE
                    lastSevereSpeakMs = 0L
                    out.add(Directive.ApplySevereControls)
                    out.add(Directive.LogEvent("severe_controls_on"))
                    out.add(
                        Directive.Notify(
                            title = "Overheating: ${fmt(tempC)}Â°C",
                            body = "Stop heavy use now. Never put the phone in a fridge or on ice; shade and air only. The phone's own thermal safety stays in charge.",
                            severe = true
                        )
                    )
                    speakSevere(out, nowMs, tempC, charging, force = true)
                }
                else -> { zone = newZone }
            }
        } else {
            zone = newZone
        }

        // --- phases inside an active/severe episode ---
        if (zone == Zone.ACTIVE || zone == Zone.SEVERE) {
            val elapsed = nowMs - episodeStartMs
            when {
                phase == Phase.OBSERVE && elapsed >= OBSERVE_MS -> {
                    phase = Phase.IDENTIFY
                    out.add(Directive.IdentifyHeavyApps)
                    identifyDone = true
                    out.add(Directive.LogEvent("identify_apps"))
                }
                phase == Phase.IDENTIFY && elapsed >= IDENTIFY_END_MS -> {
                    phase = Phase.TREND
                    out.add(Directive.LogEvent("trend_watch"))
                }
                phase == Phase.TREND && elapsed >= TREND_END_MS -> {
                    // stay in TREND; escalate de-escalate only through temperature itself
                    out.add(Directive.LogEvent("trend_held"))
                }
            }
            if (!identifyDone && phase == Phase.IDENTIFY) {
                out.add(Directive.IdentifyHeavyApps)
                identifyDone = true
            }
            if (zone == Zone.ACTIVE) speakActive(out, nowMs, tempC, charging)
            if (zone == Zone.SEVERE) speakSevere(out, nowMs, tempC, charging, force = false)
        }

        // --- recovery (phase D), sustained below 37 ---
        if (controlsApplied && tempC <= RECOVER_C) {
            val since = belowRecoverSinceMs ?: nowMs.also { belowRecoverSinceMs = it }
            if (nowMs - since >= RECOVER_HOLD_MS) {
                out.add(Directive.RestoreControls)
                out.add(Directive.ClearNotification)
                out.add(Directive.Speak("Phone cooled to ${fmt(tempC)} degrees. Screen and controls restored.", critical = false))
                out.add(Directive.LogEvent("recovered"))
                endEpisode()
            }
        } else {
            belowRecoverSinceMs = null
        }

        // --- session close when fully cool ---
        if (zone == Zone.COOL && episodeStartMs != 0L) {
            val since = belowCoolSinceMs ?: nowMs.also { belowCoolSinceMs = it }
            if (nowMs - since >= COOL_END_HOLD_MS) {
                out.add(Directive.LogEvent("session_closed_cool"))
                out.add(Directive.ClearNotification)
                endEpisode()
            }
        } else {
            belowCoolSinceMs = null
        }

        out.add(Directive.SampleGap(gapFor(zone)))
        return out
    }

    private fun startEpisode(nowMs: Long, tempC: Float, charging: Boolean?) {
        episodeStartMs = nowMs
        episodeStartTemp = tempC
        episodeMaxTemp = tempC
        episodeCharging = charging
        phase = Phase.OBSERVE
        identifyDone = false
        controlsApplied = true
        belowRecoverSinceMs = null
        belowCoolSinceMs = null
    }

    private fun endEpisode() {
        phase = Phase.IDLE
        controlsApplied = false
        episodeStartMs = 0L
        episodeStartTemp = null
        episodeMaxTemp = 0f
        episodeCharging = null
        identifyDone = false
    }

    private fun speakActive(out: ArrayList<Directive>, nowMs: Long, tempC: Float, charging: Boolean?) {
        if (nowMs - lastActiveSpeakMs < ACTIVE_SPEAK_COOLDOWN_MS) return
        lastActiveSpeakMs = nowMs
        val chargeHint = when (charging) {
            true -> " Charging while hot is slower on purpose; remove the case if it is safe to do so."
            false -> ""
            null -> ""
        }
        out.add(Directive.Speak("Phone hot, ${fmt(tempC)} degrees. Close heavy apps and give it air.$chargeHint", critical = true))
    }

    private fun speakSevere(out: ArrayList<Directive>, nowMs: Long, tempC: Float, charging: Boolean?, force: Boolean) {
        if (!force && nowMs - lastSevereSpeakMs < SEVERE_SPEAK_COOLDOWN_MS) return
        lastSevereSpeakMs = nowMs
        val chargeHint = if (charging == true) " Unplug the charger if you can." else ""
        out.add(Directive.Speak("Overheating, ${fmt(tempC)} degrees. Stop heavy use now.$chargeHint Never use a fridge or ice.", critical = true))
    }

    /**
     * User-triggered TEST: runs the real detection path once against the real current
     * readings and reports exactly what it found. It never dims the screen in test mode.
     */
    fun runTest(nowMs: Long, tempC: Float?, charging: Boolean?, thermalStatus: Int? = null): List<Directive> {
        val out = ArrayList<Directive>()
        if (tempC == null) {
            out.add(Directive.Speak("Test: the battery temperature sensor is unavailable on this phone right now.", critical = false, test = true))
            out.add(Directive.LogEvent("test_sensor_unavailable"))
            return out
        }
        val z = zoneFor(tempC)
        val zoneName = when (z) {
            Zone.COOL -> "cool, no action needed"
            Zone.MONITOR -> "monitor only"
            Zone.LIGHT -> "light control"
            Zone.ACTIVE -> "active control"
            Zone.SEVERE -> "severe"
        }
        out.add(
            Directive.Speak(
                "Test: thermal guardian is on. Temperature ${fmt(tempC)} degrees, zone $zoneName. " +
                    if (z.ordinal >= Zone.LIGHT.ordinal) "Real controls are active." else "No controls applied because the phone is not hot.",
                critical = z.ordinal >= Zone.ACTIVE.ordinal,
                test = true
            )
        )
        if (z.ordinal >= Zone.LIGHT.ordinal) out.add(Directive.IdentifyHeavyApps)
        out.add(Directive.LogEvent("test_run zone=$z temp=$tempC charging=$charging thermalStatus=$thermalStatus"))
        return out
    }

    /** Snapshot for the UI card. */
    data class Status(
        val zone: Zone,
        val phase: Phase,
        val controlsApplied: Boolean,
        val episodeStartMs: Long,
        val episodeStartTemp: Float?,
        val episodeMaxTemp: Float,
        val episodeCharging: Boolean?
    )

    fun status(): Status = Status(zone, phase, controlsApplied, episodeStartMs, episodeStartTemp, episodeMaxTemp, episodeCharging)

    private fun fmt(c: Float): String = String.format(java.util.Locale.US, "%.1f", c)
}
