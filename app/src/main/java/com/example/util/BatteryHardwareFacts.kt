package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Plain battery facts Android exposes to any app without any permission (the sticky battery
 * broadcast). Nothing here is estimated: health is the battery's own self-report, voltage and
 * temperature are the phone's sensors, level is the system reading. Apps CANNOT measure real
 * capacity (mAh) or cycle count on Android - we never show a number for those.
 */
object BatteryHardwareFacts {

    data class Facts(
        val levelPercent: Int,
        val temperatureC: Float,
        val voltageMv: Int,
        val healthLabel: String,
        val charging: Boolean
    )

    fun read(context: Context): Facts? {
        return try {
            val i: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good (self-reported)"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat reported"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead reported"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage reported"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold reported"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure reported"
                else -> "Unavailable"
            }
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            Facts(
                levelPercent = level * 100 / scale,
                temperatureC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f,
                voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
                healthLabel = health,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            )
        } catch (t: Throwable) {
            null
        }
    }
}
