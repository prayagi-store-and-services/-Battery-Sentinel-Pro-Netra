package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.festival.Banner
import com.example.festival.BannerKind
import com.example.festival.FestivalBanner
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.delay

/** Shows today's festival, Independence Day or condolence. Hidden when there is nothing to show. Data is bundled, works offline. */
@Composable
fun FestivalBannerCard(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val banner: Banner = remember(java.text.SimpleDateFormat("yyyyMMdd").format(now)) {
        FestivalBanner.pick(Calendar.getInstance())
    } ?: return
    val b = Color(banner.accent2)
    val sorrow = banner.kind == BannerKind.SORROW
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(banner.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sorrow) b else MaterialTheme.colorScheme.primary)
        Text(banner.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(now),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
