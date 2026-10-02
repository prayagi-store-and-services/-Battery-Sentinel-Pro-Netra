package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.update.GitHubReleaseUpdater
import com.example.update.UpdateUiState

/** Manual update check with an honest status line (never hides a failed check). */
@Composable
fun UpdateCheckCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updater = GitHubReleaseUpdater.shared(context)
    val state by updater.state.collectAsStateWithLifecycle()
    val status = when (val s = state) {
        is UpdateUiState.Idle -> "Installed: v" + BuildConfig.VERSION_NAME + ". Not checked yet in this session."
        is UpdateUiState.Checking -> "Checking GitHub for a newer version..."
        is UpdateUiState.UpToDate -> "You are up to date (v" + s.versionName + ")."
        is UpdateUiState.Available -> "Update available: " + s.release.versionName + ". Tap Update in the popup."
        is UpdateUiState.Downloading -> "Downloading " + s.release.versionName + "..."
        is UpdateUiState.Error -> "Update check failed: " + s.message
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "App updates", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { updater.checkNow() },
                enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading
            ) { Text("Check for updates") }
        }
    }
}
