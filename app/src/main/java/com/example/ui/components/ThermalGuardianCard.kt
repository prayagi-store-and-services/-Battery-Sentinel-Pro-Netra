package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.NetraApplication
import com.example.model.DotState
import com.example.service.ThermalGuardianLog
import com.example.service.ThermalGuardianPolicy
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thermal Guardian card on the Status screen.
 * Shows the live zone/phase, the on/off switch, a TEST button that exercises the real
 * detection path, and the last measured cooling session. Every number shown is a real
 * reading; a phone that reports no temperature says Unavailable instead of guessing.
 */
@Composable
fun ThermalGuardianCard(modifier: Modifier = Modifier) {
    val controller = NetraApplication.instance.thermalGuardianController
    val ui by controller.ui.collectAsState()
    val sessions by ThermalGuardianLog.sessions.collectAsState()
    val settings by NetraApplication.instance.settingsRepository.settings.collectAsState()

    val (dot, accent) = when (ui.zone) {
        ThermalGuardianPolicy.Zone.CRITICAL -> DotState.CRITICAL to StatusRed
        ThermalGuardianPolicy.Zone.SEVERE -> DotState.CRITICAL to StatusRed
        ThermalGuardianPolicy.Zone.ACTIVE -> DotState.CRITICAL to StatusRed
        ThermalGuardianPolicy.Zone.LIGHT -> DotState.THROTTLED to StatusAmber
        ThermalGuardianPolicy.Zone.MONITOR -> DotState.THROTTLED to StatusAmber
        ThermalGuardianPolicy.Zone.COOL -> DotState.CONNECTED to NetraEmerald
    }

    SentinelCard(
        title = "Thermal Guardian",
        icon = Icons.Default.DeviceThermostat,
        dotState = dot,
        accentColor = accent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when {
                        ui.lastTempC == null -> "Temperature: Unavailable on this phone"
                        else -> "Temperature: ${String.format(Locale.US, "%.1f", ui.lastTempC)}°C"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Zone: ${ui.zone.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                        if (ui.controlsApplied) " - controls active" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (ui.charging == true) "Charging" else "Not charging",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.thermalGuardianEnabled,
                onCheckedChange = { enabled ->
                    NetraApplication.instance.settingsRepository.setThermalGuardianEnabled(enabled)
                    if (enabled) controller.start() else controller.stop()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = NetraCyan, checkedTrackColor = NetraCyan.copy(alpha = 0.3f)),
                modifier = Modifier.testTag("thermal_guardian_switch")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { controller.runTest() },
            colors = ButtonDefaults.buttonColors(containerColor = NetraCyan),
            modifier = Modifier.testTag("thermal_guardian_test_button")
        ) {
            Text("Test Thermal Guardian", fontSize = 13.sp)
        }
        if (ui.lastTestAtMs > 0L) {
            Text(
                text = "Last test: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ui.lastTestAtMs))}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Honest limits: the app cannot cool the phone by itself. When hot, it dims the screen (with your permission), cuts its own work, names recently used heavy apps and warns. Cooling is not guaranteed; the phone's own safety stays in charge.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val lastReal = sessions.lastOrNull { !it.isTest }
        if (lastReal != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Last cooling session", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
            Text(
                text = buildString {
                    append(fmt.format(Date(lastReal.startMs)))
                    append(": ${String.format(Locale.US, "%.1f", lastReal.startTempC)}°C")
                    lastReal.endTempC?.let { append(" -> ${String.format(Locale.US, "%.1f", it)}°C") }
                    lastReal.coolingRateCPerMin?.let { append(", ${String.format(Locale.US, "%.2f", it)}°C/min") }
                    append(if (lastReal.reachedSafeBand) ", back under 37°C" else ", still warm")
                    append(if (lastReal.chargingAtStart == true) " (charging)" else " (on battery)")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
