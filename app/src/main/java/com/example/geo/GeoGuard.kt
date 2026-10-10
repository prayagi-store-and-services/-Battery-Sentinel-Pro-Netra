package com.example.geo

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * On-device region guard. Reads only the phone's own SIM / network country and its language region.
 * No permission, no network call, no IP lookup, nothing is stored or sent.
 * To block another country, add its two-letter code to [BLOCKED].
 */
object GeoGuard {
    val BLOCKED: Set<String> = setOf("PK", "BD", "AF", "CN", "KP")

    /** Pure decision. Fails open: an empty or unknown signal never blocks. An Indian SIM never blocks. */
    fun decide(sim: String?, network: String?, localeRegion: String?, blocked: Set<String> = BLOCKED): Boolean {
        val s = sim?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val n = network?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (s == "IN") return false
        val telephony = listOf(s, n).filter { it.isNotEmpty() }
        if (telephony.isNotEmpty()) return telephony.any { it in blocked }
        val l = localeRegion?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return l.isNotEmpty() && l in blocked
    }

    fun isBlocked(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        decide(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
    } catch (_: Exception) {
        false
    }
}
