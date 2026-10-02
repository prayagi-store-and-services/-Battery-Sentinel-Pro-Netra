package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.util.FeedbackBuilder
import com.example.util.FeedbackPayload
import com.example.util.FeedbackSender
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional, consent-based feedback and crash report sender. Nothing is sent without the user pressing Send. */
@Composable
fun FeedbackCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val crashFile = remember { File(context.filesDir, "pending_crash_report.txt") }
    var hasCrash by remember { mutableStateOf(crashFile.exists()) }
    var dialogKind by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var failedPayload by remember { mutableStateOf<FeedbackPayload?>(null) }

    fun currentPayload(kind: String): FeedbackPayload {
        val model = Build.MODEL ?: "Unknown"
        val android = Build.VERSION.RELEASE ?: "Unknown"
        return if (kind == "Crash") {
            FeedbackBuilder.crash(model, android, BuildConfig.VERSION_NAME, runCatching { crashFile.readText() }.getOrDefault(""))
        } else {
            FeedbackBuilder.feedback(model, android, BuildConfig.VERSION_NAME, message)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Feedback and crash reports", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optional. Nothing is sent unless you press Send. You will see exactly what is sent first: phone model, Android version, app version, and your message or the crash stack trace. Nothing else.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { status = null; failedPayload = null; dialogKind = "Feedback" }, modifier = Modifier.fillMaxWidth()) {
                Text("Send feedback")
            }
            if (hasCrash) {
                Button(onClick = { status = null; failedPayload = null; dialogKind = "Crash" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send last crash report")
                }
            } else {
                Text("No crash recorded since the last report.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FeedbackBuilder.PRIVACY_URL)))
            }) { Text("Privacy policy") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            failedPayload?.let { p ->
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:prayagideepak@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "[Netra] In-app " + p.kind)
                        putExtra(Intent.EXTRA_TEXT, p.disclosure())
                    }
                    runCatching { context.startActivity(intent) }
                }) { Text("Send by email app instead") }
            }
        }
    }

    val kind = dialogKind
    if (kind != null) {
        val payload = currentPayload(kind)
        AlertDialog(
            onDismissRequest = { if (!sending) dialogKind = null },
            title = { Text(if (kind == "Crash") "Send crash report?" else "Send feedback?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == "Feedback") {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it.take(FeedbackBuilder.MAX_MESSAGE) },
                            label = { Text("Your message") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(payload.disclosure(), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sending && (kind == "Crash" || message.isNotBlank()),
                    onClick = {
                        sending = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { FeedbackSender.send(payload) }
                            sending = false
                            dialogKind = null
                            if (ok) {
                                status = "Sent. Thank you!"
                                if (kind == "Crash") { runCatching { crashFile.delete() }; hasCrash = false } else message = ""
                            } else {
                                status = "Could not send right now (check your internet)."
                                failedPayload = payload
                            }
                        }
                    }
                ) { Text(if (sending) "Sending..." else "Send") }
            },
            dismissButton = { TextButton(enabled = !sending, onClick = { dialogKind = null }) { Text("Cancel") } }
        )
    }
}
