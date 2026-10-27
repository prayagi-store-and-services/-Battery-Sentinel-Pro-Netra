package com.example.ui.components

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetraCentralState
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber

@Composable
fun CircularBatteryGauge(
    canonical: NetraCentralState,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .testTag("circular_battery_gauge")
    ) {
        BatteryDotRings(canonical.batteryLevel, canonical.temperatureCelsius,
            canonical.isDataFresh && canonical.fieldStates.levelStatus == com.example.model.FieldStatus.LIVE &&
                canonical.fieldStates.tempStatus == com.example.model.FieldStatus.LIVE,
            canonical.isScreenOn, Modifier.widthIn(max = 300.dp).fillMaxWidth())
        val status = when(canonical.isCharging) {
            true -> listOfNotNull(canonical.pluggedType?.name, canonical.chargingSpeed.displayLabel,
                canonical.powerWatts?.let { String.format(java.util.Locale.getDefault(), "%.1f W", it) }).joinToString(" | ")
            false -> if (canonical.isChargerConnected == true) "Plugged in | Idle" else "On battery | Discharging"
            null -> "Status unavailable"
        }
        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val eta = if (canonical.isCharging == true) canonical.chargingEtaMinutes?.let { "Full in ~$it min" }
            else canonical.dischargingEtaMinutes?.let { "~$it min remaining" }
        Text(eta ?: "ETA unavailable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        TelemetryFreshnessSummary(canonical)

        // Telemetry Bar (including explicit retained-reading status)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryMetric(
                label = "VOLTAGE",
                value = canonical.voltageMv?.let { "$it mV" } ?: "Unavailable",
                accent = NetraCyan
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            TelemetryMetric(
                label = "CURRENT",
                value = canonical.currentMa?.let { "$it mA" } ?: "Unavailable",
                accent = if ((canonical.currentMa ?: 0) >= 0) NetraEmerald else StatusAmber
            )
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            TelemetryMetric(
                label = "POWER",
                value = canonical.powerWatts?.let { "${String.format("%.2f", it)} W" } ?: "Unavailable",
                accent = if (canonical.isCharging == true) NetraEmerald else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun TelemetryFreshnessSummary(canonical: NetraCentralState) {
    val fields = canonical.fieldStates
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = if (canonical.isDataFresh) "Battery observation received" else "Live battery update unavailable",
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        fun status(status: com.example.model.FieldStatus, at: Long): String = when (status) {
            com.example.model.FieldStatus.LIVE -> "Observed"
            com.example.model.FieldStatus.LAST_VALID -> "Last known"
            com.example.model.FieldStatus.UNSUPPORTED -> "Unsupported"
            com.example.model.FieldStatus.UNAVAILABLE -> "Unavailable"
        } + if (at > 0L) " at " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at)) else ""
        Text("Level: " + status(fields.levelStatus, fields.levelObservedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Temperature: " + status(fields.tempStatus, fields.tempObservedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Voltage: " + status(fields.voltageStatus, fields.voltageObservedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Current: " + status(fields.currentStatus, fields.currentObservedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Power: " + status(fields.powerStatus, fields.powerObservedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Last-known readings are retained values, not new measurements.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
