package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.TargetAlert

/** Shows whether the charge target alert is armed or has fired, and lets the user test that it can reach them. */
@Composable
fun TargetAlertStatusCard() {
    val c = LocalContext.current
    val settings by com.example.NetraApplication.instance.settingsRepository.settings.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
    val time = if (TargetAlert.lastFiredAtMs > 0L) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(TargetAlert.lastFiredAtMs)) else ""
    val text = TargetAlert.statusText(settings.unplugAlarmEnabled, settings.chargeTargetPercent, TargetAlert.lastFiredAtMs, TargetAlert.lastFiredLevel, TargetAlert.lastFiredTarget, time)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = text + if (tick < 0) "" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { result = TargetAlert.sendTest(c) }, modifier = Modifier.padding(top = 8.dp)) { Text("Test alert") }
        result?.let { Text(text = it, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}
