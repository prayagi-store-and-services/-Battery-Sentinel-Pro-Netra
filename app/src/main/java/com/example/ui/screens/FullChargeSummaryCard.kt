package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.example.service.FullChargeSummaryStore
import com.example.ui.components.SentinelCard
import com.example.ui.theme.NetraEmerald
import java.text.DateFormat
import java.util.Date

@Composable
fun FullChargeSummaryCard(value: FullChargeSummaryStore.Completed) {
    val s = value.observation
    SentinelCard(title = "Last observed full-charge session", icon = Icons.Default.Bolt,
        accentColor = NetraEmerald) {
        Text("Disconnected: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value.disconnectedAt))}")
        Text("First observed 100%: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.firstFullWallMs))}")
        Text("From ${s.startPercent}% to first observed 100%: ${interval(s.observedToFullMs)} connected")
        Text("Connected after first observed 100%: ${interval(s.connectedAfterFullMs)}")
        Text("Observed intervals, not exact physical completion time. Gaps over 15 minutes, unknown readings and app restarts break the observation window. No battery-damage estimate.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun interval(ms: Long): String = if (ms < 60_000) "under 1 min" else "${ms / 60_000} min"
