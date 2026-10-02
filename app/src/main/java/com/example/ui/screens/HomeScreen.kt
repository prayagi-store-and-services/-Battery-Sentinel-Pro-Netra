package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DotState
import com.example.ui.components.CircularBatteryGauge
import com.example.ui.components.SentinelCard
import com.example.ui.navigation.NetraTab
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel

@Composable
fun HomeScreen(
    viewModel: NetraViewModel,
    onNavigateTab: (NetraTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val canonical by viewModel.canonicalState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Charging Active Shortcut Card
        if (canonical.isCharging == true) {
            SentinelCard(
                title = "⚡ Live Charging Telemetry Active",
                icon = Icons.Default.Bolt,
                dotState = DotState.CONNECTED,
                accentColor = NetraEmerald
            ) {
                Text(
                    text = "Charging session is actively streaming live 1-second electrical telemetry and animated fill dynamics.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onNavigateTab(NetraTab.BATTERY) },
                    modifier = Modifier.fillMaxWidth().testTag("home_view_live_charging_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetraEmerald
                    )
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Live Charging Monitor", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Battery Overview
        SentinelCard(
            title = "Nethra Overview",
            icon = Icons.Default.Dashboard,
            dotState = telemetry.serviceDotState,
            accentColor = NetraEmerald
        ) {
            CircularBatteryGauge(canonical = canonical)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level: ${canonical.batteryLevel?.let { "$it%" } ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                Text("Temp: ${canonical.temperatureCelsius?.let { "${String.format("%.1f", it)}°C" } ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        // Active Warnings/Status
        if (canonical.isCriticalThermalActive) {
            SentinelCard(title = "⚠️ CRITICAL THERMAL", icon = Icons.Default.Warning, dotState = DotState.CRITICAL, accentColor = StatusRed) {
                Text("Thermal protection is active to prevent damage.", color = StatusRed)
            }
        }
    }
}
