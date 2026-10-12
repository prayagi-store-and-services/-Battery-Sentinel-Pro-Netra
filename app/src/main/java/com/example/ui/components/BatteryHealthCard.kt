package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.ui.theme.NetraEmerald
import java.io.File
import java.util.Locale

/** Real battery health readings that this phone chooses to share. Anything the phone hides is simply not shown. */
internal data class BatteryHealthReading(
    val cycleCount: Int?,
    val designMah: Int?,
    val fullChargeMah: Int?,
    val fullChargeIsEstimate: Boolean
) {
    val hasAnything get() = cycleCount != null || designMah != null || fullChargeMah != null
    val healthPct: Int? get() =
        if (designMah != null && designMah > 0 && fullChargeMah != null) ((fullChargeMah * 100L) / designMah).toInt().coerceIn(1, 120) else null
}

private fun readSysfsMah(name: String): Int? {
    return try {
        val f = File("/sys/class/power_supply/battery/$name")
        if (!f.exists() || !f.canRead()) return null
        val raw = f.readText().trim().toLongOrNull() ?: return null
        if (raw <= 0L) return null
        // Phones report micro-amp-hours (e.g. 5000000) or milli-amp-hours (e.g. 5000).
        val mah = if (raw >= 100_000L) raw / 1000L else raw
        if (mah in 500L..30_000L) mah.toInt() else null
    } catch (_: Exception) { null }
}

internal fun readBatteryHealth(c: Context): BatteryHealthReading {
    val sticky: Intent? = try { c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
    // Android 14 added a cycle count to the battery broadcast; older phones simply do not send it.
    val cycles = sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
    val design = readSysfsMah("charge_full_design")
    var full = readSysfsMah("charge_full")
    var estimate = false
    if (full == null) {
        // Estimate: the phone's charge counter (what is stored now) divided by the battery level.
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val counterUah = try { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0 } catch (_: Exception) { 0 }
        val level = try { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0 } catch (_: Exception) { 0 }
        if (counterUah > 0 && level in 20..100) {
            val est = (counterUah.toLong() * 100L / level / 1000L).toInt()
            if (est in 500..30_000) { full = est; estimate = true }
        }
    }
    return BatteryHealthReading(cycles, design, full, estimate)
}

/** Battery health card. Hidden completely when the phone shares none of these values. */
@Composable
fun BatteryHealthCard() {
    val c = LocalContext.current
    val r = remember { readBatteryHealth(c) }
    if (!r.hasAnything) return
    SentinelCard(title = "Battery Health", icon = Icons.Default.Favorite, dotState = DotState.CONNECTED, accentColor = NetraEmerald) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.healthPct?.let {
                Text("$it% of original capacity" + if (r.fullChargeIsEstimate) " (estimated)" else "", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            r.designMah?.let { Text("Design capacity: $it mAh (reported by the phone)", fontSize = 13.sp) }
            r.fullChargeMah?.let {
                Text(
                    "Full-charge capacity: $it mAh (" + if (r.fullChargeIsEstimate) "estimated from the charge counter and level)" else "reported by the phone)",
                    fontSize = 13.sp
                )
            }
            r.cycleCount?.let { Text("Charge cycles: $it (reported by the phone)", fontSize = 13.sp) }
            Text(
                "Read on this phone only; nothing is sent anywhere. Phones round and sometimes misreport these values, so treat them as a guide.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
