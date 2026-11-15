package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NetraEmerald

/** The switch controls the shared announcement gate, including the one-time greeting. */
@Composable
internal fun MediaPlaybackSkipCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(NetraEmerald.copy(alpha = 0.08f))
        .border(1.dp, NetraEmerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Skip during media playback", Modifier.weight(1f),
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = NetraEmerald,
                    checkedTrackColor = NetraEmerald.copy(alpha = 0.3f)),
                modifier = Modifier.testTag("media_skip_switch"))
        }
        Text(if (enabled) "ON - recommended" else "OFF - speech may overlap media",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("On: Skip announcements while Android detects media playing. Skipped announcements are not replayed later.\n\nOff: Announcements may speak over media. Media will not be paused or resumed.\n\nSome silent video, cast or hidden playback may not be detected.",
            fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
