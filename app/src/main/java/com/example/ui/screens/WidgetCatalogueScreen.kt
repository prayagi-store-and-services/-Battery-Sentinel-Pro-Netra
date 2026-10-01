package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.widget.BatteryQuickProvider
import com.example.widget.BatteryStatsProvider
import com.example.widget.BatteryFullProvider
import com.example.widget.TemperatureProvider
import com.example.widget.VoltageProvider
import com.example.widget.CurrentProvider
import com.example.widget.PowerProvider
import com.example.widget.HealthProvider
import com.example.widget.GraphCurrentProvider
import com.example.widget.GraphPowerProvider
import com.example.widget.GraphTempProvider
import com.example.widget.GraphCurrentVoltageProvider
import com.example.widget.GraphWattageVoltageProvider
import kotlinx.coroutines.launch

private data class WidgetOption(
    val name: String,
    val size: String,
    val provider: Class<*>
)

private val availableWidgets = listOf(
    WidgetOption("Battery Quick", "146 × 72 dp", BatteryQuickProvider::class.java),
    WidgetOption("Battery Stats", "250 × 180 dp", BatteryStatsProvider::class.java),
    WidgetOption("Battery Overview", "250 × 180 dp", BatteryFullProvider::class.java),
    WidgetOption("Battery Temperature", "250 × 72 dp", TemperatureProvider::class.java),
    WidgetOption("Battery Voltage", "250 × 72 dp", VoltageProvider::class.java),
    WidgetOption("Battery Current", "250 × 72 dp", CurrentProvider::class.java),
    WidgetOption("Battery Power", "250 × 72 dp", PowerProvider::class.java),
    WidgetOption("Battery Health", "250 × 72 dp", HealthProvider::class.java),
    WidgetOption("Current Trend", "250 × 72 dp", GraphCurrentProvider::class.java),
    WidgetOption("Power Trend", "250 × 72 dp", GraphPowerProvider::class.java),
    WidgetOption("Temperature Trend", "250 × 72 dp", GraphTempProvider::class.java),
    WidgetOption("Current and Voltage Trend", "250 × 72 dp", GraphCurrentVoltageProvider::class.java),
    WidgetOption("Wattage and Voltage Trend", "250 × 72 dp", GraphWattageVoltageProvider::class.java)
)

@Composable
fun WidgetCatalogueScreen(viewModel: com.example.viewmodel.NetraViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Home screen widgets",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Choose a widget and follow the Android launcher prompt to add it.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(availableWidgets, key = { it.provider.name }) { widget ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(widget.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Minimum size: ${widget.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                            onClick = {
                                val manager = AppWidgetManager.getInstance(context)
                                val component = ComponentName(context, widget.provider)
                                if (!manager.isRequestPinAppWidgetSupported) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Your launcher does not support direct widget pinning. Add this widget from the Home screen widget picker."
                                        )
                                    }
                                } else {
                                    val accepted = manager.requestPinAppWidget(component, null, null)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (accepted) "Widget request sent to the launcher."
                                            else "The launcher did not accept the widget request."
                                        )
                                    }
                                }
                            }
                        ) {
                            Text("Add to Home Screen")
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                item {
                    Text(
                        "Direct add is supported on Android 8.0 and newer. On older versions, add widgets from the Home screen widget picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
