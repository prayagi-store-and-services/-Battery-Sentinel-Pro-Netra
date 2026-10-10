package com.example

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.data.local.NetraDatabase
import com.example.data.repository.BatteryRepository
import com.example.data.repository.SettingsRepository
import com.example.service.BatteryMonitorService
import com.example.service.NetraCentralDataCenter

class NetraApplication : Application(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()

    val startedAtMillis: Long = System.currentTimeMillis()

    lateinit var database: NetraDatabase
        private set

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var centralDataCenter: NetraCentralDataCenter
        private set

    lateinit var capabilityRegistry: com.example.service.CentralCapabilityRegistry
        private set

    lateinit var storageCacheManager: com.example.data.repository.StorageCacheManager
        private set

    lateinit var calibrationManager: com.example.ai.BatteryCalibrationManager
        private set

    lateinit var powerProfileManager: com.example.ai.PowerProfileManager
        private set

    lateinit var announcementEngine: com.example.service.AnnouncementEngine
        private set

    lateinit var telemetrySentinel: com.example.service.TelemetrySentinel
        private set

    lateinit var stabilitySentinel: com.example.service.StabilitySentinel
        private set

    lateinit var idealStateEngine: com.example.service.IdealStateEngine
        private set

    lateinit var chargingOptimizationEngine: com.example.service.ChargingOptimizationEngine
        private set

    lateinit var liveChargingSessionEngine: com.example.service.LiveChargingSessionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        try { Brand.applyLauncherName(this) } catch (_: Throwable) { }
        instance = this
        // Remove any installer file left from an in-app update (background thread).
        Thread { com.example.update.UpdateFileCleanup.cleanLeftovers(applicationContext) }.start()
        database = NetraDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        centralDataCenter = NetraCentralDataCenter()
        capabilityRegistry = com.example.service.CentralCapabilityRegistry(this)
        centralDataCenter.initPersistence(this)
        centralDataCenter.initCapabilityRegistry(this, capabilityRegistry)
        storageCacheManager = com.example.data.repository.StorageCacheManager(this)
        announcementEngine = com.example.service.AnnouncementEngine(this)
        telemetrySentinel = com.example.service.TelemetrySentinel(this)
        stabilitySentinel = com.example.service.StabilitySentinel.installCrashHandler(this)
        // A crash report saved by the last crash is sent now, in the background, with no user action.
        val crashCtx = applicationContext
        Thread { com.example.util.CrashAutoSender.sendPending(crashCtx) }.start()
        idealStateEngine = com.example.service.IdealStateEngine(this)
        chargingOptimizationEngine = com.example.service.ChargingOptimizationEngine(this)
        liveChargingSessionEngine = com.example.service.LiveChargingSessionEngine(
            hardwareProvider = com.example.service.AndroidBatteryHardwareProvider(this)
        )
        com.example.service.StabilityHealthScheduler.schedule(this)
        com.example.update.UpdateCheckWorker.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            stabilitySentinel.flushPendingReports()
        }
        calibrationManager = com.example.ai.BatteryCalibrationManager(this)
        powerProfileManager = com.example.ai.PowerProfileManager(this)
        batteryRepository = BatteryRepository(database.batteryDao(), database.chargingSessionDao(), database.activityLogDao())

        // Initialize Bluetooth profile proxy services
        try {
            com.example.util.BluetoothHelper.initialize(this)
        } catch (_: Exception) {}

        // Start 24/7 low-power service safely
        try {
            BatteryMonitorService.startService(this)
        } catch (_: Exception) {}

        // Start Battery Foreground Service tracking percentage, temperature, and charging status
        try {
            com.example.service.BatteryForegroundService.startService(this)
        } catch (_: Exception) {}
    }

    companion object {
        lateinit var instance: NetraApplication
            private set
    }
}
