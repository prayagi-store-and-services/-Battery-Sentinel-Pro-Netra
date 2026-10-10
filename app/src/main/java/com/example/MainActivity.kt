package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.StatusDot
import com.example.ui.navigation.NetraBottomNav
import com.example.ui.navigation.NetraTab
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MonitoringScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NetraCyan
import com.example.ui.theme.NetraDarkBg
import com.example.ui.theme.NetraEmerald
import com.example.ui.theme.NetraSurface
import com.example.ui.theme.StatusRed
import com.example.viewmodel.NetraViewModel
import com.example.update.GitHubReleaseUpdater
import com.example.update.UpdateUiState

open class MainActivity : ComponentActivity() {

    private val viewModel: NetraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.example.geo.GeoGuard.isBlocked(this)) {
            val pad = (24 * resources.displayMetrics.density).toInt()
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(pad, pad, pad, pad)
            }
            box.addView(android.widget.TextView(this).apply {
                text = "This app is not available in your region."
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            })
            box.addView(android.widget.Button(this).apply {
                text = "Close"
                setOnClickListener { finishAffinity() }
            })
            setContentView(box)
            return
        }
        enableEdgeToEdge()

        // Anonymous daily usage count (+1 on a public counter, nothing else). The user can turn it off in Settings.
        val appCtx = applicationContext
        Thread { com.example.stats.UsagePing.pingIfDue(appCtx) }.start()

        try {
            com.example.service.BatteryMonitorService.startService(this)
            com.example.service.BatteryForegroundService.startService(this)
        } catch (_: Exception) {}

        handleNetworkPanelExtra(intent)
        handleAutoUpdateExtra(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        BrightnessTouch.lastMs = android.os.SystemClock.elapsedRealtime()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNetworkPanelExtra(intent)
        handleAutoUpdateExtra(intent)
    }

    /** Tapping the update notification downloads the newest verified build and starts the install. */
    private fun handleAutoUpdateExtra(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(com.example.update.UpdateCheckWorker.EXTRA_AUTO_UPDATE, false) != true) return
        intent.removeExtra(com.example.update.UpdateCheckWorker.EXTRA_AUTO_UPDATE)
        android.widget.Toast.makeText(this, "Downloading the update...", android.widget.Toast.LENGTH_SHORT).show()
        GitHubReleaseUpdater.shared(this).checkAndInstall { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun handleNetworkPanelExtra(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(com.example.service.NetworkSwitchCoordinator.EXTRA_OPEN_NETWORK_PANEL, false) == true) {
            intent.removeExtra(com.example.service.NetworkSwitchCoordinator.EXTRA_OPEN_NETWORK_PANEL)
            try {
                startActivity(android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } catch (_: Exception) {
                try { startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) } catch (_: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Thread { com.example.update.UpdateFileCleanup.cleanStale(applicationContext) }.start()
        viewModel.refreshHardwareState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: NetraViewModel) {
    var currentTab by remember { mutableStateOf(NetraTab.HOME) }
    val telemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val canonical by viewModel.canonicalState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val releaseUpdater = remember(context) { GitHubReleaseUpdater.shared(context) }
    val updateState by releaseUpdater.state.collectAsStateWithLifecycle()

    LaunchedEffect(releaseUpdater) {
        releaseUpdater.checkNow()
        releaseUpdater.startPeriodicChecks(this)
    }

    // Thermal & Low Battery Brightness Protection Lock
    // The protection dim (10%) only applies while the screen is idle. Any touch brings normal brightness back at once,
    // and it comes back for good as soon as the protection ends (charger connected, cooled down, battery recovered).
    LaunchedEffect(canonical.targetBrightnessPercent) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val target = canonical.targetBrightnessPercent
        if (window != null) {
            var applied: Float? = null
            while (true) {
                val idle = android.os.SystemClock.elapsedRealtime() - com.example.BrightnessTouch.lastMs >= 15_000L
                val want = if (target != null && idle) (target / 100f).coerceIn(0.01f, 1.0f)
                    else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                if (applied != want) {
                    val lp = window.attributes
                    lp.screenBrightness = want
                    window.attributes = lp
                    applied = want
                }
                if (target == null) break
                kotlinx.coroutines.delay(300L)
            }
        }
    }

    // Permissions are now explained one at a time (Approve / Skip) instead of firing system dialogs blindly.
    com.example.permissions.PermissionOnboardingHost(onChanged = { viewModel.refreshHardwareState() })

    var updateDismissedFor by remember { mutableStateOf(-1L) }
    val downloadingState = updateState as? UpdateUiState.Downloading
    // The dialog stays open while the download runs, so the user sees the progress instead of it vanishing.
    val availableUpdate = (updateState as? UpdateUiState.Available)?.release ?: downloadingState?.release
    if (availableUpdate != null && (updateDismissedFor != availableUpdate.versionCode || downloadingState != null)) {
        AlertDialog(
            onDismissRequest = { updateDismissedFor = availableUpdate.versionCode },
            title = { Text("Update available: " + availableUpdate.versionName) },
            text = {
                Column {
                    Text("A newer ${com.example.Brand.name} release is available.")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("What's new", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(availableUpdate.notes.ifBlank { "Release notes are unavailable." })
                    downloadingState?.let { ds ->
                        Spacer(modifier = Modifier.size(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { ds.fraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(ds.line.ifBlank { "Starting the download..." }, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        releaseUpdater.installLatest(onError = { message ->
                            android.widget.Toast.makeText(context, "Update problem: $message", android.widget.Toast.LENGTH_LONG).show()
                        })
                    },
                    enabled = updateState !is UpdateUiState.Downloading
                ) {
                    Text(if (updateState is UpdateUiState.Downloading) "Downloading…" else "Update")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { updateDismissedFor = availableUpdate.versionCode }) {
                    Text("Later")
                }
            }
        )
    }

    // BackHandler: return to Home tab if on secondary tab
    BackHandler(enabled = currentTab != NetraTab.HOME) {
        currentTab = NetraTab.HOME
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            // Standard Netra header: 56 dp, only app name, version and date/time. Everything else scrolls.
            var clockNow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(java.util.Date()) }
            androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { clockNow = java.util.Date(); kotlinx.coroutines.delay(30_000) } }
            val ownVersion = androidx.compose.runtime.remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { null } ?: "Unavailable" }
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "${com.example.Brand.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "v" + ownVersion, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(clockNow), fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        bottomBar = {
            NetraBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                label = "tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                run {
                    when (tab) {
                        NetraTab.HOME -> HomeScreen(
                            viewModel = viewModel,
                            topContent = {
                                com.example.ui.components.FestivalBannerCard(modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    androidx.compose.material3.TextButton(onClick = { viewModel.refreshHardwareState() }, modifier = Modifier.testTag("top_bar_refresh_button")) { Text("Refresh", fontSize = 12.sp, color = NetraCyan) }
                                }
                            },
                            onNavigateTab = { target ->
                                            currentTab = target
                            }
                        )
                        NetraTab.BATTERY -> BatteryScreen(viewModel = viewModel)
                        NetraTab.MONITORING -> MonitoringScreen(viewModel = viewModel)
                        NetraTab.DEVICES -> DevicesScreen(viewModel = viewModel)
                        NetraTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

/** Time of the last touch in the main window; the protection dim waits 15 s after it. */
object BrightnessTouch {
    @Volatile var lastMs: Long = android.os.SystemClock.elapsedRealtime()
}
