package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs

/** One live reading taken straight from BatteryManager. Null fields mean the device did not report them. */
data class LivePowerReading(
    val voltageV: Float?,
    val currentMa: Int?,
    val isCharging: Boolean,
    val watts: Float?
) {
    fun wattsText(): String = watts?.let { String.format(Locale.US, "%.2f W", abs(it)) } ?: "Unavailable"
    fun voltageText(): String = voltageV?.let { String.format(Locale.US, "%.3f V", it) } ?: "Unavailable"
    fun currentText(): String = currentMa?.let { "${abs(it)} mA" } ?: "Unavailable"

    companion object {
        /** Pure math, unit tested. rawCurrent is BATTERY_PROPERTY_CURRENT_NOW (microamps by spec, some phones give milliamps). */
        fun from(voltageMv: Int, rawCurrent: Int, isCharging: Boolean): LivePowerReading {
            val v = if (voltageMv > 0) voltageMv / 1000f else null
            val currentOk = rawCurrent != Int.MIN_VALUE && rawCurrent != Int.MAX_VALUE
            val ma = if (currentOk) ChargingSpeedEngine().normalizeToMilliAmps(rawCurrent, true) else null
            val w = if (v != null && ma != null) abs(v * ma / 1000f) else null
            return LivePowerReading(v, ma, isCharging, w)
        }

        fun read(c: Context): LivePowerReading {
            val intent: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val charging = plugged && (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val raw = try { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE } catch (_: Exception) { Int.MIN_VALUE }
            return from(mv, raw, charging)
        }
    }
}
