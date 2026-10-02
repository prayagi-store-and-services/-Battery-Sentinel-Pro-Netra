package com.example.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Shows at most one explanation popup at a time. First time: Approve / Skip.
 * After a skip, on a later launch: allow now / remind later / don't ask again.
 * Granted permissions are never shown. Approve opens Android's own screen or dialog.
 */
@Composable
fun PermissionOnboardingHost(onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { PermissionPrefs(context) }
    var launch by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(setOf<Perm>()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { launch = prefs.countLaunchOnce() }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { tick++; onChanged() } }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var pending by remember { mutableStateOf<Perm?>(null) }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; onChanged() }

    if (launch == 0) return
    val observedTick = tick
    if (observedTick < 0) return
    val current = Perm.values().firstOrNull { p ->
        p !in shown && PermissionPromptPolicy.decide(
            PermissionCheck.isGranted(context, p), PermissionCheck.isRelevant(context, p), prefs.get(p), launch
        ) != Prompt.NONE
    } ?: return
    val kind = PermissionPromptPolicy.decide(
        PermissionCheck.isGranted(context, current), PermissionCheck.isRelevant(context, current), prefs.get(current), launch
    )

    fun close(choice: Choice?) {
        if (choice != null) prefs.set(current, choice, launch)
        shown = shown + current
    }

    fun openSystem() {
        // Provisionally "skipped": if the user really grants it, it is never shown again; if they back out, it is re-asked later.
        prefs.set(current, Choice.SKIPPED, launch)
        shown = shown + current
        val rt = PermissionCheck.runtimePermission(current)
        if (rt != null) {
            runtimeLauncher.launch(rt)
        } else {
            val i = PermissionCheck.settingsIntent(context, current)
            try {
                if (i != null) context.startActivity(i) else context.startActivity(PermissionCheck.appDetailsIntent(context))
            } catch (_: Exception) {
                runCatching { context.startActivity(PermissionCheck.appDetailsIntent(context)) }
            }
        }
    }

    if (kind == Prompt.FIRST) {
        AlertDialog(
            onDismissRequest = { close(Choice.SKIPPED) },
            title = { Text(PermissionCopy.title(current)) },
            text = { Text(PermissionCopy.benefit(current) + "\n\n" + PermissionCopy.HOW) },
            confirmButton = { TextButton(onClick = { openSystem() }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { close(Choice.SKIPPED) }) { Text("Skip") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = { close(Choice.REMIND_LATER) },
            title = { Text(PermissionCopy.REASK_TITLE) },
            text = { Text(PermissionCopy.title(current) + ": " + PermissionCopy.benefit(current) + "\n\n" + PermissionCopy.HOW) },
            confirmButton = {
                Column {
                    TextButton(onClick = { openSystem() }) { Text("Abhi allow karein") }
                    TextButton(onClick = { close(Choice.REMIND_LATER) }) { Text("Baad mein yaad dilayein") }
                    TextButton(onClick = { close(Choice.NEVER) }) { Text("Dobara mat poochho") }
                }
            }
        )
    }
}
