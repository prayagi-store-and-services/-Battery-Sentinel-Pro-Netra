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

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = NetraDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(this)
        centralDataCenter = NetraCentralDataCenter()
        capabilityRegistry = com.example.service.CentralCapabilityRegistry(this)
        centralDataCenter.initPersistence(this)
        centralDataCenter.initCapabilityRegistry(this)
        storageCacheManager = com.example.data.repository.StorageCacheManager(this)
        announcementEngine = com.example.service.AnnouncementEngine(this)
        telemetrySentinel = com.example.service.TelemetrySentinel(this)
        stabilitySentinel = com.example.service.StabilitySentinel.installCrashHandler(this)
        idealStateEngine = com.example.service.IdealStateEngine(this)
        chargingOptimizationEngine = com.example.service.ChargingOptimizationEngine(this)
        com.example.service.StabilityHealthScheduler.schedule(this)
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
    }

    companion object {
        lateinit var instance: NetraApplication
            private set
    }
}
