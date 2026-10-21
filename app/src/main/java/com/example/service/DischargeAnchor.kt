package com.example.service

import android.content.Context

/** Where the moment the charger was last removed is kept, so an app update does not restart it. */
interface DischargeAnchorStore {
    fun get(): Long?
    fun set(epochMs: Long)
    fun clear()
}

/**
 * Keeps the real "on battery since" time in the app's private storage (a small file that
 * survives an app update, but not uninstall or "Clear data"). No network, nothing shared.
 */
class PrefsDischargeAnchorStore(context: Context) : DischargeAnchorStore {
    private val prefs = context.applicationContext.getSharedPreferences("discharge_anchor", Context.MODE_PRIVATE)
    override fun get(): Long? = if (prefs.contains(KEY)) prefs.getLong(KEY, 0L) else null
    override fun set(epochMs: Long) { prefs.edit().putLong(KEY, epochMs).apply() }
    override fun clear() { prefs.edit().remove(KEY).apply() }
    private companion object { const val KEY = "on_battery_since_epoch_ms" }
}

object DischargeAnchor {
    /** Called on every battery state: a connected charger ends the window, the first unplugged state starts it. */
    fun observe(store: DischargeAnchorStore, chargerConnected: Boolean?, nowEpochMs: Long) {
        when (chargerConnected) {
            true -> store.clear()
            false -> if (store.get() == null) store.set(nowEpochMs)
            null -> Unit
        }
    }

    /**
     * Returns the monotonic-clock start of the on-battery window. A saved start (from before an
     * update or a restart) is converted to the current monotonic clock; with none saved, the
     * window starts now and is saved. Never returns 0, which the engine uses for "not started".
     */
    fun elapsedStart(store: DischargeAnchorStore?, nowEpochMs: Long, nowElapsedMs: Long): Long {
        val saved = store?.get()
        val start = if (saved != null && saved <= nowEpochMs) {
            nowElapsedMs - (nowEpochMs - saved)
        } else {
            store?.set(nowEpochMs)
            nowElapsedMs
        }
        return if (start == 0L) -1L else start
    }
}
