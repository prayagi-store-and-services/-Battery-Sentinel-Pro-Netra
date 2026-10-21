package com.example.service

import android.content.Context

/**
 * The charger and charging session time stamps the app shows ("session started", on battery since).
 * They are saved so an app update or restart does not start them again from zero.
 */
data class SessionSnapshot(
    val chargerConnectedAt: Long?,
    val chargerDisconnectedAt: Long?,
    val chargingStartedAt: Long?,
    val chargingStoppedAt: Long?,
    val dischargingStartedAt: Long?,
    val connected: Boolean?,
    val charging: Boolean?,
    val discharging: Boolean
)

interface SessionSnapshotStore {
    fun load(): SessionSnapshot?
    fun save(s: SessionSnapshot)
}

/** Private app storage only: nothing is sent anywhere or shared. Removed by uninstall or Clear data. */
class PrefsSessionSnapshotStore(context: Context) : SessionSnapshotStore {
    private val p = context.applicationContext.getSharedPreferences("central_session_snapshot", Context.MODE_PRIVATE)

    private fun long(k: String): Long? = if (p.contains(k)) p.getLong(k, 0L) else null
    private fun bool(k: String): Boolean? = if (p.contains(k)) p.getBoolean(k, false) else null

    override fun load(): SessionSnapshot? {
        if (!p.contains("saved")) return null
        return SessionSnapshot(
            long("cc"), long("cd"), long("chs"), long("che"), long("ds"),
            bool("conn"), bool("chg"), p.getBoolean("dis", false)
        )
    }

    override fun save(s: SessionSnapshot) {
        val e = p.edit().putBoolean("saved", true)
        fun l(k: String, v: Long?) { if (v == null) e.remove(k) else e.putLong(k, v) }
        fun b(k: String, v: Boolean?) { if (v == null) e.remove(k) else e.putBoolean(k, v) }
        l("cc", s.chargerConnectedAt); l("cd", s.chargerDisconnectedAt)
        l("chs", s.chargingStartedAt); l("che", s.chargingStoppedAt); l("ds", s.dischargingStartedAt)
        b("conn", s.connected); b("chg", s.charging); e.putBoolean("dis", s.discharging)
        e.apply()
    }
}
