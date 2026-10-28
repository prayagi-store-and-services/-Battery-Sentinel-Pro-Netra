package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.*

@Composable
fun CrashRecoveryNotice() {
    val context = LocalContext.current
    val until by CrashRecoveryGate.pauseUntil.collectAsStateWithLifecycle()
    val failures by CrashRecoveryGate.startFailures.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    // No countdown or retry loop. A later normal start rechecks expiry; explicit retry acts now.
    val retry: () -> Unit = {
        if (CrashRecoveryGate.retry(context)) {
            BatteryMonitorService.startService(context)
            BatteryForegroundService.startService(context)
        } else message = "Retry could not be saved. Monitoring remains paused."
    }
    if (until <= 0L && failures.isNotEmpty()) {
        Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text("Monitoring start was not accepted: ${failures.joinToString()}. Background monitoring and thermal alerts may be unavailable. Android may restrict starts.")
                Button(onClick = retry) { Text("Retry monitoring now") }
            }
        }
    }
    if (until > 0L) CrashRecoveryCard(until, message, retry)
}


@Composable
internal fun CrashRecoveryCard(until: Long, message: String?, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monitoring paused after repeated crashes", style = MaterialTheme.typography.titleMedium)
            Text("Background monitoring and thermal alerts may be unavailable. Do not rely on Netra for safety while paused. Follow your phone maker's instructions.", style = MaterialTheme.typography.bodyMedium)
            Text("Three uncaught crashes within five minutes pause automatic service starts for two minutes. A later normal start can resume after the pause; Android can still refuse it. There is no scheduled restart.", style = MaterialTheme.typography.bodySmall)
            val date = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(until))
            Text("Automatic start eligible after $date. Retry now clears the crash counter.", style = MaterialTheme.typography.bodySmall)
            if (message != null) Text(message)
            Button(onClick = onRetry) { Text("Retry monitoring now") }
        }
    }
}
