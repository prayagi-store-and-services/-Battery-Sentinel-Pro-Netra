package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.repository.CacheStorageStats

@Composable
fun CacheStorageDetails(stats: CacheStorageStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Disposable cache file payload", style = MaterialTheme.typography.titleSmall)
        if (stats.scanAvailable) {
        Text("Internal: ${stats.internalCacheBytes / 1024} KiB; external: ${stats.externalCacheBytes / 1024} KiB", style = MaterialTheme.typography.bodySmall)
        Text("Total: ${stats.totalCacheBytes / (1024 * 1024)} MiB. " + if (stats.isOverThreshold) "Above 200 MiB." else "At or below 200 MiB.", style = MaterialTheme.typography.bodySmall)
        } else { Text("Cache size: Unavailable (scan incomplete or not yet run). Automatic cleanup skipped.", style = MaterialTheme.typography.bodySmall) }
        Text("Automatic cleanup only above 200 MiB, at most once per six hours. Charging does not lower the threshold. Code cache, links, protected files and saved data are excluded.", style = MaterialTheme.typography.bodySmall)
        if (stats.lastCleanupTimestamp > 0L) {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(stats.lastCleanupTimestamp))
            Text("Last cleanup: $date. Removed ${stats.lastFreedBytes / 1024} KiB of file payload; folder allocation is not counted.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
