package com.example.community

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CommunityCard(open: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val launch: (String) -> Unit = { url ->
        failed = false
        try { if (open != null) open(url) else CommunityBrowser.open(context, url) }
        catch (_: Exception) { failed = true }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect with Netra Ecosystem", style = MaterialTheme.typography.titleLarge)
            Text("Official profiles open only when tapped. Nothing is followed, liked or posted automatically.", style = MaterialTheme.typography.bodySmall)
            CommunityLinks.social.forEach { link ->
                OutlinedButton(onClick = { launch(link.url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(link.icon + "  " + link.label)
                }
            }
            OutlinedButton(onClick = { launch(CommunityLinks.WEBSITE) }, modifier = Modifier.fillMaxWidth()) { Text("Battery website") }
            OutlinedButton(onClick = { launch(CommunityLinks.CONTACT) }, modifier = Modifier.fillMaxWidth()) { Text("Private complaint or feedback") }
            Text("The private web form sends your message through FormSubmit. It is separate from public app reviews. No reward or required follow.", style = MaterialTheme.typography.bodySmall)
            if (failed) Text("Could not open this link. Check that a browser is available.", color = MaterialTheme.colorScheme.error)
        }
    }
}
