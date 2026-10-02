package com.example.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DotState
import com.example.model.LiveChargingSessionState
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import java.util.Locale

/**
 * Dedicated Live Charging Monitor section adhering strictly to the Final Coding Command:
 * Displays all 7 required live telemetry fields while charging:
 * 1. Voltage: XXXX.xx mV
 * 2. Electric Current: XXXX.xx mA
 * 3. Wattage: XX.XX W
 * 4. Battery Temperature: XX.X °C
 * 5. Battery Percentage: XX.xx%
 * 6. Charging Duration: HH:mm:ss
 * 7. Estimated Time to Full: HH:mm:ss
 */
@Composable
fun LiveChargingMonitorSection(
    sessionState: LiveChargingSessionState,
    modifier: Modifier = Modifier
) {
    val isCharging = sessionState.isChargingActive

    // 1. Voltage: XXXX.xx mV
    val voltageMv = sessionState.currentVoltageMv
    val voltageFormatted = if (isCharging && voltageMv != null) {
        String.format(Locale.US, "%.2f mV", voltageMv)
    } else {
        "Unavailable"
    }

    // 2. Electric Current: XXXX.xx mA
    val currentMa = sessionState.currentCurrentMa
    val currentFormatted = if (isCharging && currentMa != null) {
        String.format(Locale.US, "%.2f mA", currentMa)
    } else {
        "Unavailable"
    }

    // 3. Wattage: XX.XX W (Power (W) = Voltage (mV) × Current (mA) / 1,000,000)
    val wattageW = sessionState.currentPowerWatts
    val wattageFormatted = if (isCharging && wattageW != null) {
        String.format(Locale.US, "%.2f W", wattageW)
    } else {
        "Unavailable"
    }

    // 4. Battery Temperature: XX.X °C
    val tempC = sessionState.currentTemperatureCelsius
    val tempFormatted = if (tempC != null) {
        String.format(Locale.US, "%.1f °C", tempC)
    } else {
        "Unavailable"
    }

    // 5. Battery Percentage: XX.xx%
    val percentVal = sessionState.currentBatteryPercent
    val percentFormatted = if (percentVal != null) {
        String.format(Locale.US, "%.2f%%", percentVal)
    } else {
        "Unavailable"
    }

    // 6. Charging Duration: HH:mm:ss
    val durationSec = sessionState.sessionDurationSeconds
    val durHours = durationSec / 3600
    val durMinutes = (durationSec % 3600) / 60
    val durSeconds = durationSec % 60
    val durationFormatted = if (isCharging) {
        String.format(Locale.US, "%02d:%02d:%02d", durHours, durMinutes, durSeconds)
    } else {
        "00:00:00"
    }

    // 7. Estimated Time to Full: HH:mm:ss
    val etaFormatted = if (isCharging) {
        sessionState.etaDisplayStatus ?: "Calculating..."
    } else {
        "Unavailable"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_charging_monitor_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Discharging Banner if not charging
        if (!isCharging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .testTag("discharging_inactive_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerOff,
                        contentDescription = "Discharging / Inactive",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "ON BATTERY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Charger is not connected. Live discharge data refreshes every second while this screen is open. Values Android does not report are hidden.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!isCharging) {
            DischargeTelemetryCard(sessionState = sessionState)
        } else {
        // REQUIRED 7-FIELD CANONICAL LIVE CHARGING DASHBOARD
        SentinelCard(
            title = if (isCharging) "Live Charging Telemetry" else "Charging Monitor (Idle / Discharging)",
            icon = Icons.Default.Bolt,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = if (isCharging) NetraEmerald else NetraCyan,
            trailingAction = {
                val badgeText = if (isCharging) "LIVE 1s REFRESH" else "IDLE"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isCharging) NetraEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCharging) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Primary Telemetry List with Exact Required Labels and Formats
                CanonicalTelemetryLine(
                    label = "Voltage:",
                    value = voltageFormatted,
                    tag = "voltage_display",
                    color = if (voltageFormatted != "Unavailable") NetraCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
                CanonicalTelemetryLine(
                    label = "Electric Current:",
                    value = currentFormatted,
                    tag = "current_display",
                    color = if (currentFormatted != "Unavailable") StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                CanonicalTelemetryLine(
                    label = "Wattage:",
                    value = wattageFormatted,
                    tag = "wattage_display",
                    color = if (wattageFormatted != "Unavailable") NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    isProminent = true
                )
                CanonicalTelemetryLine(
                    label = "Battery Temperature:",
                    value = tempFormatted,
                    tag = "temperature_display",
                    color = if ((tempC ?: 0f) >= 40f) StatusRed else MaterialTheme.colorScheme.onSurface
                )
                CanonicalTelemetryLine(
                    label = "Battery Percentage:",
                    value = percentFormatted,
                    tag = "percentage_display",
                    color = NetraEmerald
                )
                CanonicalTelemetryLine(
                    label = "Charging Duration:",
                    value = durationFormatted,
                    tag = "duration_display",
                    color = MaterialTheme.colorScheme.onSurface
                )
                CanonicalTelemetryLine(
                    label = "Estimated Time to Full:",
                    value = etaFormatted,
                    tag = "time_to_full_display",
                    color = if (etaFormatted == "00:00:00") NetraEmerald else NetraCyan
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Wattage is calculated battery-side electrical power (Voltage × Current), not guaranteed adapter rating.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }

        // Animated Battery Graphic
        SentinelCard(
            title = if (isCharging) "Battery Level Dynamics" else "Battery Status (Discharging)",
            icon = Icons.Default.ElectricMeter,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = if (isCharging) NetraEmerald else NetraCyan
        ) {
            LiveBatteryGraphic(
                batteryPercent = sessionState.currentBatteryPercent,
                isCharging = isCharging,
                pluggedSource = sessionState.pluggedSource
            )
        }

        if (isCharging) {
        // Live Session Timing & Charging Source Details
        SentinelCard(
            title = "Charging Source & Monotonic Timing",
            icon = Icons.Default.Schedule,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraEmerald
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CHARGING SOURCE",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sessionState.pluggedSource ?: "Unavailable",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetraCyan,
                        modifier = Modifier.testTag("charging_source_value")
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CLOCK SOURCE",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Elapsed Realtime",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "STATUS",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isCharging) "Active (~1s)" else "Idle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCharging) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }

        // Live Charging Graph
        SentinelCard(
            title = if (isCharging) "Live Charging Session Graph" else "Live Discharge Graph",
            icon = Icons.AutoMirrored.Filled.ShowChart,
            dotState = DotState.CONNECTED,
            accentColor = if (isCharging) NetraEmerald else NetraCyan
        ) {
            LiveChargingGraph(
                samples = if (isCharging) sessionState.rollingHistory else sessionState.dischargeHistory,
                emptyText = if (isCharging) "Awaiting charging telemetry samples (~1/sec)..." else "Collecting live discharge samples (~1/sec)..."
            )
        }

        // Session Telemetry History Table
        SentinelCard(
            title = "Session Telemetry History",
            icon = Icons.Default.History,
            dotState = DotState.CONNECTED,
            accentColor = NetraCyan
        ) {
            LiveChargingHistoryTable(samples = if (isCharging) sessionState.rollingHistory else sessionState.dischargeHistory)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CanonicalTelemetryLine(
    label: String,
    value: String,
    tag: String,
    color: Color,
    isProminent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isProminent) NetraSurface.copy(alpha = 0.7f) else Color.Transparent)
            .padding(horizontal = if (isProminent) 8.dp else 4.dp, vertical = if (isProminent) 6.dp else 2.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isProminent) 13.sp else 12.sp,
            fontWeight = if (isProminent) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            fontSize = if (isProminent) 16.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}


/**
 * Live on-battery card. Shows only values Android actually reports; a field with no data is not drawn.
 * Power is the observed battery-side draw (voltage x current magnitude).
 */
@Composable
private fun DischargeTelemetryCard(sessionState: LiveChargingSessionState) {
    val rows = buildList<Triple<String, String, Color>> {
        sessionState.dischargePowerWatts?.let {
            add(Triple("Power draw:", String.format(Locale.US, "%.2f W", it), NetraEmerald))
        }
        sessionState.dischargeCurrentMa?.let {
            add(Triple("Discharge current:", String.format(Locale.US, "%.2f mA", it), StatusAmber))
        }
        sessionState.dischargeVoltageMv?.let {
            add(Triple("Voltage:", String.format(Locale.US, "%.2f mV", it), NetraCyan))
        }
        sessionState.currentTemperatureCelsius?.let {
            add(Triple("Battery Temperature:", String.format(Locale.US, "%.1f °C", it), if (it >= 40f) StatusRed else Color.Unspecified))
        }
        sessionState.currentBatteryPercent?.let {
            add(Triple("Battery Percentage:", String.format(Locale.US, "%.2f%%", it), NetraEmerald))
        }
        val s = sessionState.dischargeDurationSeconds
        add(Triple("On battery for:", String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60), Color.Unspecified))
    }
    SentinelCard(
        title = "Live Discharge Telemetry",
        icon = Icons.Default.Bolt,
        dotState = DotState.CONNECTED,
        accentColor = NetraCyan,
        trailingAction = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetraCyan.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "LIVE 1s REFRESH", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = NetraCyan)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value, color) ->
                CanonicalTelemetryLine(
                    label = label,
                    value = value,
                    tag = "discharge_" + label.lowercase(Locale.US).filter { it.isLetter() },
                    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
                )
            }
        }
    }
}
