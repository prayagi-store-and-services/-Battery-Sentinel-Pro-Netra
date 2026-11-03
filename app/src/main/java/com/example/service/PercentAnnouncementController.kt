package com.example.service

import android.content.Context
import android.util.Log
import com.example.NetraApplication

/**
 * Speaks the battery percentage when it crosses a step boundary, under the opt-in Battery
 * Percentage Announcement feature. Discharging always announces while the feature is on (every 5
 * percent, or 10 when the user switches); charging announces only when its own switch is on. All
 * state is in memory: direction changes reset the tracking, there are no repeats and no catch-up
 * announcements. Voice only, through the existing announcement engine, so master announcements and
 * Night Protection rules apply unchanged.
 */
class PercentAnnouncementController(private val context: Context) {

    private var lastLevel: Int? = null
    private var lastCharging: Boolean? = null

    /** Call with every central state update. */
    @Synchronized
    fun onState(level: Int?, isCharging: Boolean?, isConnected: Boolean?) {
        val settings = try {
            NetraApplication.instance.settingsRepository.settings.value
        } catch (_: Exception) { null } ?: return

        val charging = isCharging == true || isConnected == true
        if (lastCharging != null && lastCharging != charging) lastLevel = null
        lastCharging = charging

        if (!settings.percentAnnouncementEnabled || !settings.announcementsMasterEnabled || level == null || level !in 0..100) {
            lastLevel = level
            return
        }
        val step = when {
            charging && !settings.percentChargingEnabled -> { lastLevel = level; return }
            charging -> settings.percentChargingStep
            else -> if (settings.percentDischargingStep10) 10 else 5
        }
        val mark = PercentAnnouncement.crossed(lastLevel, level, charging, step)
        lastLevel = level
        if (mark == null) return
        try {
            NetraApplication.instance.announcementEngine.enqueue(
                AnnouncementItem(
                    id = "percent_announcement_${mark}_${System.currentTimeMillis()}",
                    text = "Battery $mark percent",
                    priority = AnnouncementPriority.PHONE_BATTERY,
                    category = PercentAnnouncement.CATEGORY,
                    isNightException = false
                )
            )
            Log.i(TAG, "Announced $mark% (${if (charging) "charging" else "discharging"}, step $step)")
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "NetraPercentAnnounce"
    }
}
