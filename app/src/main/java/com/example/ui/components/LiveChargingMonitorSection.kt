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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Live Charging Monitor section.
 * Renders large animated battery graphic, electrical telemetry cards, live session graph,
 * and timestamped history. Activates strictly during confirmed charging sessions.
 */
@Composable
fun LiveChargingMonitorSection(
    sessionState: LiveChargingSessionState,
    modifier: Modifier = Modifier
) {
    val isCharging = sessionState.isChargingActive
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

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
                            text = "CHARGER DISCONNECTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live Charging Monitor activates automatically when plugged in and actively charging. The 1-second telemetry loop and animation are inactive while discharging to prevent battery drain.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // A & B. Large Animated Battery Graphic with Prominent Percentage
        SentinelCard(
            title = if (isCharging) "Live Battery Charge Dynamics" else "Battery Status (Discharging)",
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

        // C, D, E. Electrical Telemetry Cards (Voltage, Current, Calculated Charging Power)
        SentinelCard(
            title = "Live Electrical Telemetry (~1s Refresh)",
            icon = Icons.Default.Bolt,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraCyan
        ) {
            // E. Calculated Charging Power (Prominently displayed)
            val powerWatts = sessionState.currentPowerWatts
            val powerDisplayStr = when {
                !isCharging -> "Unavailable (Not Charging)"
                powerWatts == null -> "Unavailable"
                powerWatts < 1.0f -> "${String.format(Locale.US, "%.2f", powerWatts * 1000f)} mW"
                else -> "${String.format(Locale.US, "%.2f", powerWatts)} W"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "CALCULATED CHARGING POWER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = powerDisplayStr,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCharging && powerWatts != null) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("power_card_value")
                )
                Text(
                    text = "Calculated battery-side electrical power (not USB wall adapter rating).",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // C & D. Voltage and Current Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Voltage Card
                val voltageV = sessionState.currentVoltageV
                val voltageStr = if (voltageV != null) String.format(Locale.US, "%.3f V", voltageV) else "Unavailable"

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .testTag("voltage_card")
                ) {
                    Column {
                        Text(
                            text = "BATTERY VOLTAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetraCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = voltageStr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("voltage_card_value")
                        )
                    }
                }

                // Current Card
                val currentA = sessionState.currentCurrentA
                val currentStr = when {
                    !isCharging -> "Unavailable"
                    currentA != null -> String.format(Locale.US, "%.3f A", currentA)
                    else -> "Unavailable"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .testTag("current_card")
                ) {
                    Column {
                        Text(
                            text = "CHARGING CURRENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAmber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentStr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("current_card_value")
                        )
                    }
                }
            }
        }

        // F. Charging Source & Session Duration Card
        SentinelCard(
            title = "Session Timing & Charging Source",
            icon = Icons.Default.Schedule,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraEmerald
        ) {
            val durationSec = sessionState.sessionDurationSeconds
            val hours = durationSec / 3600
            val minutes = (durationSec % 3600) / 60
            val seconds = durationSec % 60
            val durationStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

            val lastUpdatedStr = sessionState.lastUpdatedTimeMs?.let { timeFormat.format(Date(it)) } ?: "Unavailable"
            val sourceStr = sessionState.pluggedSource ?: "Unavailable"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSION DURATION",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isCharging) durationStr else "00:00:00 (Idle)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isCharging) NetraEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("session_duration_value")
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CHARGING SOURCE",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sourceStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetraCyan,
                        modifier = Modifier.testTag("charging_source_value")
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "LAST UPDATED",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lastUpdatedStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("last_updated_value")
                    )
                }
            }
        }

        // G. Live Graph
        SentinelCard(
            title = "Live Charging Session Graph",
            icon = Icons.Default.ShowChart,
            dotState = if (isCharging) DotState.CONNECTED else DotState.STANDBY,
            accentColor = NetraEmerald
        ) {
            LiveChargingGraph(samples = sessionState.rollingHistory)
        }

        // H. Scrollable Timestamped Charging History
        SentinelCard(
            title = "Session Telemetry History",
            icon = Icons.Default.History,
            dotState = DotState.CONNECTED,
            accentColor = NetraCyan
        ) {
            LiveChargingHistoryTable(samples = sessionState.rollingHistory)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
