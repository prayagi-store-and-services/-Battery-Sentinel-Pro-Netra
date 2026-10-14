package com.example.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.receiver.NetraDeviceAdmin

/**
 * Optional "lock the screen when charging stops". It needs Android's Device Admin permission with the force-lock policy only.
 * Off by default. The lock happens only when the always-on charging screen was on display and charging has stopped
 * (unplugged, or paused), so it never locks a phone someone is using. On-device only; nothing is stored or sent.
 */
object ScreenLock {
    const val PREFS = "screen_lock_prefs"
    const val KEY_ENABLED = "lock_on_charge_stop"

    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(c: Context) = ComponentName(c, NetraDeviceAdmin::class.java)

    fun isAdminActive(c: Context): Boolean = try { dpm(c).isAdminActive(admin(c)) } catch (_: Exception) { false }

    fun isEnabled(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply() }

    /** Intent for Android's own screen that asks the user to turn the permission on. */
    fun requestIntent(c: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(c))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets the app lock the screen when charging stops. It cannot read, change or erase anything on the phone.")

    /** Gives the permission back to Android, so the app can be uninstalled as usual. */
    fun removeAdmin(c: Context) { try { dpm(c).removeActiveAdmin(admin(c)) } catch (_: Exception) {}; setEnabled(c, false) }

    /** Pure rule: lock only when the user opted in, the permission is active and the charging screen was showing. */
    fun shouldLock(enabled: Boolean, adminActive: Boolean, chargingScreenShowing: Boolean): Boolean =
        enabled && adminActive && chargingScreenShowing

    /** Locks the screen if the rule allows. Returns true when a lock was requested. */
    fun lockIfAllowed(c: Context, chargingScreenShowing: Boolean): Boolean {
        if (!shouldLock(isEnabled(c), isAdminActive(c), chargingScreenShowing)) return false
        return try { dpm(c).lockNow(); true } catch (_: Exception) { false }
    }
}
