package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Step guide for Android's "Restricted setting" block. It is a drawn illustration, not a real screenshot or
 * recording, and it says so. The highlighted step moves on by itself every 2.5 seconds, or the user can tap a step.
 * Shown only where the restriction is likely (see restrictedSettingsLikely). It runs no background work.
 */
@Composable
fun RestrictedSettingsGuide() {
    val steps = listOf(
        "Press and hold this app's icon on the home screen or app list" to "[ icon ]  hold",
        "Tap \"App info\"" to "[ App info ]",
        "In App info, tap the three dots at the top right" to "[ ... ]",
        "Tap \"Allow restricted settings\" and confirm with your PIN or fingerprint" to "[ Allow restricted settings ]",
        "Go back to notification access and switch it on for this app" to "[ Allow notification access  ON ]"
    )
    var current by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(current) {
        kotlinx.coroutines.delay(2500)
        current = (current + 1) % steps.size
    }
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.material3.Text("Step by step (drawn illustration, not a real screenshot):", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        steps.forEachIndexed { i, s ->
            val on = i == current
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(if (on) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .clickable { current = i }
                    .padding(8.dp)
            ) {
                androidx.compose.material3.Text("${i + 1}. ${s.first}", fontSize = 12.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                if (on) {
                    androidx.compose.material3.Text(s.second, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
        }
        androidx.compose.material3.Text("After you tap \"Allow restricted settings\" the option disappears from the menu. That is normal: it means it worked. Go back and switch notification access on. Menu names can differ a little on your phone.", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
