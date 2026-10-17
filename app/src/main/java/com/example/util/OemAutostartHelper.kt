package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * OEM "auto-launch / autostart" guidance.
 *
 * On realme UI, ColorOS, MIUI, HyperOS, FuntouchOS and EMUI the phone kills background apps even
 * when Android's own battery-optimization exemption is granted. The separate OEM "auto-launch"
 * switch is what keeps a 24/7 monitor alive. Android offers no API for it, so this helper only:
 *  - knows which brands need the extra switch,
 *  - tries the brand's known settings screens (community-documented components),
 *  - says Unavailable honestly when the phone has none of them (never pretends).
 */
object OemAutostartHelper {

    private val AFFECTED_BRANDS = setOf(
        "realme", "oppo", "oneplus", "xiaomi", "redmi", "poco", "vivo", "iqoo", "huawei"
    )

    /** Brand needs the OEM auto-launch switch for reliable background monitoring. */
    fun needsAttention(manufacturer: String): Boolean =
        manufacturer.trim().lowercase() in AFFECTED_BRANDS

    // (package, activity) pairs, best-known first per brand family.
    private val CANDIDATES: List<Pair<String, String>> = listOf(
        // realme UI / ColorOS / Oppo / OnePlus
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        // MIUI / HyperOS (Xiaomi, Redmi, POCO)
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // FuntouchOS (vivo, iQOO)
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // EMUI (Huawei)
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
    )

    /** Pure: first candidate whose package is in [resolvablePackages]; null when none. Testable. */
    fun firstResolvable(resolvablePackages: Set<String>): Pair<String, String>? =
        CANDIDATES.firstOrNull { it.first in resolvablePackages }

    /** The phone's auto-launch settings screen, or null when this build has none of the known screens. */
    fun resolveIntent(context: Context): Intent? {
        val pm = context.packageManager
        val first = firstResolvable(
            CANDIDATES.mapNotNull { (pkg, act) ->
                val i = Intent().setComponent(ComponentName(pkg, act))
                if (pm.resolveActivity(i, 0) != null) pkg else null
            }.toSet()
        ) ?: return null
        return Intent().setComponent(ComponentName(first.first, first.second))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
