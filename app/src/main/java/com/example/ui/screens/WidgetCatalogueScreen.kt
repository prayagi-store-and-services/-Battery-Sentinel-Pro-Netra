package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.widget.*

@Composable
fun WidgetCatalogueScreen(viewModel: com.example.viewmodel.NetraViewModel) {
    val context = LocalContext.current
    
    // List of available widgets with their provider class and metadata
    val widgets = listOf(
        Triple("Battery Quick", "2x1", BatteryQuickProvider::class.java),
        Triple("Battery Stats", "2x2", BatteryStatsProvider::class.java),
        // ... add others
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(widgets.size) { index ->
            val (name, size, providerClass) = widgets[index]
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium)
                    Text(text = size, style = MaterialTheme.typography.bodySmall)
                    
                    // Add Pinning Logic
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, providerClass)
                            if (AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) {
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            }
                        }
                    }) {
                        Text("Add to Home Screen")
                    }
                }
            }
        }
    }
}
