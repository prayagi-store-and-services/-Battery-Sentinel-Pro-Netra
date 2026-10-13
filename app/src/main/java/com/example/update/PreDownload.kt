package com.example.update

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * Rules for saving a found update in the background so the install is one tap.
 * Wi-Fi (unmetered): any size. Mobile data: only when not roaming and the file is within [MOBILE_LIMIT_BYTES].
 * Never offline, never with Data Saver on, never on a low battery, and never again when that version is already saved.
 */
object PreDownload {
    const val MOBILE_LIMIT_BYTES = 100L * 1024 * 1024

    fun shouldPreDownload(online: Boolean, unmetered: Boolean, roaming: Boolean, dataSaverOn: Boolean, batteryLow: Boolean, alreadySaved: Boolean, sizeBytes: Long): Boolean {
        if (!online || dataSaverOn || batteryLow || alreadySaved) return false
        return unmetered || (!roaming && sizeBytes in 1..MOBILE_LIMIT_BYTES)
    }

    fun allowedNow(context: Context, alreadySaved: Boolean, sizeBytes: Long): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val roaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        val saver = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val low = level >= 0 && !charging && level * 100 / scale < 20
        return shouldPreDownload(online, unmetered, roaming, saver, low, alreadySaved, sizeBytes)
    }
}
