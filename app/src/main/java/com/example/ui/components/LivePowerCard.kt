package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.model.DotState
import com.example.service.LivePowerReading
import com.example.ui.theme.NetraCyan
import kotlinx.coroutines.delay

/** Reads the battery directly once per second while this card is on screen (and the app is open). */
@Composable
fun LivePowerCard() {
    val c = LocalContext.current
    var r by remember { mutableStateOf(LivePowerReading.read(c)) }
    LaunchedEffect(Unit) {
        while (true) {
            r = LivePowerReading.read(c)
            delay(1000L)
        }
    }
    SentinelCard(title = "Live Power (updates every second)", icon = Icons.Default.Bolt, dotState = DotState.CONNECTED, accentColor = NetraCyan) {
        Text(
            text = (if (r.isCharging) "Charging: " else "Discharging: ") + r.wattsText(),
            fontSize = 28.sp, fontWeight = FontWeight.Bold
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Voltage: ${r.voltageText()}", fontSize = 14.sp)
            Text("Current: ${r.currentText()}", fontSize = 14.sp)
        }
        Text("Power = voltage x current as reported by this phone. Some phones update the current value slower than once a second.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
