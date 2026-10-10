package com.example.service

import android.content.Context
import android.net.Uri

/**
 * Reads the driving yes/no flag that Netra Hub publishes on this phone (a read-only provider, offline, nothing sent anywhere).
 * If Hub is not installed, not running or the flag is stale, this returns false and nothing changes. Only non-safety work
 * pauses while driving: Active Power Saving and the screen-off network evaluation. Thermal protection and the charge-target
 * alarm are never paused by this.
 */
object DrivingFlag {
    const val AUTHORITY = "com.aistudio.netrasensorhub.kxmpzq.driving"

    fun isDriving(c: Context): Boolean = try {
        c.applicationContext.contentResolver.query(Uri.parse("content://$AUTHORITY/state"), null, null, null, null)?.use {
            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow("driving")) == 1
        } ?: false
    } catch (_: Exception) { false }
}
