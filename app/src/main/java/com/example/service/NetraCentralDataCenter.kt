package com.example.service

import android.content.Context
import android.os.BatteryManager
import com.example.model.CanonicalChargingSpeed
import com.example.model.CanonicalPluggedType
import com.example.model.CapabilityStatus
import com.example.model.CapabilityType
import com.example.model.FieldStatus
import com.example.model.NetraCentralEvent
import com.example.model.NetraCentralState
import com.example.model.NetraEventType
import com.example.model.TelemetryFieldState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NetraCentralDataCenter(private val telemetryClock: () -> Long = { System.currentTimeMillis() }) {

    private val telemetryMutex = Mutex()
    private val mediaMutex = Mutex()
    private val bluetoothMutex = Mutex()
    private val chargingSpeedEngine = ChargingSpeedEngine()

    private val _centralState = MutableStateFlow(NetraCentralState())
    val centralState: StateFlow<NetraCentralState> = _centralState.asStateFlow()

    private val _centralEvents = MutableSharedFlow<NetraCentralEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val centralEvents: SharedFlow<NetraCentralEvent> = _centralEvents.asSharedFlow()

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var inMemoryBluetoothHistory: MutableList<com.example.model.BluetoothDeviceItem>? = null

    private var capabilityRegistry: CentralCapabilityRegistry? = null
    private var thermalInvestigator: ThermalCauseInvestigator? = null
    private var locationResolver: LocationCountryResolver? = null
    private var weatherEngine: WeatherContextEngine? = null
    private var lastValidStatePrefs: android.content.SharedPreferences? = null

    fun initCapabilityRegistry(context: Context) {
        capabilityRegistry = CentralCapabilityRegistry(context)
        thermalInvestigator = ThermalCauseInvestigator(context)
        locationResolver = LocationCountryResolver(context)
        weatherEngine = WeatherContextEngine(context)
        refreshCapabilities()
        refreshLocationAndWeather()
    }

    fun refreshLocationAndWeather(forceWeather: Boolean = false) {
        backgroundScope.launch {
            val currentLoc = locationResolver?.resolveLocationContext() ?: return@launch
            val oldState = _centralState.value

            // Field-level last-valid retention for location
            val mergedLoc = com.example.model.LocationContextState(
                countryCode = currentLoc.countryCode ?: oldState.locationContext.countryCode,
                countryName = if (currentLoc.countryName != null && currentLoc.countryName != "Unknown") currentLoc.countryName else oldState.locationContext.countryName ?: currentLoc.countryName,
                regionName = currentLoc.regionName ?: oldState.locationContext.regionName,
                locality = currentLoc.locality ?: oldState.locationContext.locality,
                locationAccuracyMeters = currentLoc.locationAccuracyMeters ?: oldState.locationContext.locationAccuracyMeters,
                locationSource = currentLoc.locationSource ?: oldState.locationContext.locationSource,
                latitude = currentLoc.latitude ?: oldState.locationContext.latitude,
                longitude = currentLoc.longitude ?: oldState.locationContext.longitude,
                permissionState = currentLoc.permissionState,
                lastUpdated = currentLoc.lastUpdated
            )

            // Fetch weather asynchronously for the location
            val currentWeather = weatherEngine?.fetchWeatherContext(mergedLoc.latitude, mergedLoc.longitude, forceWeather)
                ?: com.example.model.WeatherContextState(isAvailable = false)

            // Field-level last-valid retention for weather
            val mergedWeather = com.example.model.WeatherContextState(
                temperatureCelsius = currentWeather.temperatureCelsius ?: oldState.weatherContext.temperatureCelsius,
                feelsLikeCelsius = currentWeather.feelsLikeCelsius ?: oldState.weatherContext.feelsLikeCelsius,
                humidityPercent = currentWeather.humidityPercent ?: oldState.weatherContext.humidityPercent,
                condition = if (currentWeather.isAvailable) currentWeather.condition else oldState.weatherContext.condition,
                conditionText = currentWeather.conditionText ?: oldState.weatherContext.conditionText,
                isPrecipitating = currentWeather.isPrecipitating ?: oldState.weatherContext.isPrecipitating,
                windSpeedKmh = currentWeather.windSpeedKmh ?: oldState.weatherContext.windSpeedKmh,
                severeWeatherAlert = if (currentWeather.isAvailable) currentWeather.severeWeatherAlert else oldState.weatherContext.severeWeatherAlert,
                weatherSource = currentWeather.weatherSource ?: oldState.weatherContext.weatherSource,
                lastUpdated = currentWeather.lastUpdated,
                isAvailable = currentWeather.isAvailable || (oldState.weatherContext.temperatureCelsius != null)
            )

            // Recompute adaptive thermal context
            val newAdaptiveThermal = ClimateBaselineEngine.computeAdaptiveThermalContext(
                countryCode = mergedLoc.countryCode,
                ambientTempCelsius = mergedWeather.temperatureCelsius,
                actualBatteryTempCelsius = oldState.temperatureCelsius,
                deviceIdleState = oldState.deviceIdleState,
                isCharging = oldState.isCharging
            )

            // Single atomic canonical state update
            _centralState.value = _centralState.value.copy(
                locationContext = mergedLoc,
                weatherContext = mergedWeather,
                adaptiveThermalContext = newAdaptiveThermal
            )

            // Emit CLIMATE_CONTEXT_UPDATED event
            _centralEvents.tryEmit(
                NetraCentralEvent(
                    eventId = "event_climate_${System.currentTimeMillis()}",
                    eventType = NetraEventType.CLIMATE_CONTEXT_UPDATED,
                    timestamp = System.currentTimeMillis(),
                    previousValue = oldState.locationContext.countryCode,
                    newValue = "${mergedLoc.countryCode} • ${mergedWeather.temperatureCelsius}°C",
                    source = "ClimateEngine"
                )
            )
        }
    }

    fun updateLocationAndWeatherDirect(
        location: com.example.model.LocationContextState,
        weather: com.example.model.WeatherContextState
    ) {
        val oldState = _centralState.value
        val mergedLoc = com.example.model.LocationContextState(
            countryCode = location.countryCode ?: oldState.locationContext.countryCode,
            countryName = if (location.countryName != null && location.countryName != "Unknown") location.countryName else oldState.locationContext.countryName ?: location.countryName,
            regionName = location.regionName ?: oldState.locationContext.regionName,
            locality = location.locality ?: oldState.locationContext.locality,
            locationAccuracyMeters = location.locationAccuracyMeters ?: oldState.locationContext.locationAccuracyMeters,
            locationSource = location.locationSource ?: oldState.locationContext.locationSource,
            latitude = location.latitude ?: oldState.locationContext.latitude,
            longitude = location.longitude ?: oldState.locationContext.longitude,
            permissionState = location.permissionState,
            lastUpdated = location.lastUpdated
        )

        val mergedWeather = com.example.model.WeatherContextState(
            temperatureCelsius = weather.temperatureCelsius ?: oldState.weatherContext.temperatureCelsius,
            feelsLikeCelsius = weather.feelsLikeCelsius ?: oldState.weatherContext.feelsLikeCelsius,
            humidityPercent = weather.humidityPercent ?: oldState.weatherContext.humidityPercent,
            condition = if (weather.isAvailable) weather.condition else oldState.weatherContext.condition,
            conditionText = weather.conditionText ?: oldState.weatherContext.conditionText,
            isPrecipitating = weather.isPrecipitating ?: oldState.weatherContext.isPrecipitating,
            windSpeedKmh = weather.windSpeedKmh ?: oldState.weatherContext.windSpeedKmh,
            severeWeatherAlert = if (weather.isAvailable) weather.severeWeatherAlert else oldState.weatherContext.severeWeatherAlert,
            weatherSource = weather.weatherSource ?: oldState.weatherContext.weatherSource,
            lastUpdated = weather.lastUpdated,
            isAvailable = weather.isAvailable || (oldState.weatherContext.temperatureCelsius != null)
        )

        val newAdaptiveThermal = ClimateBaselineEngine.computeAdaptiveThermalContext(
            countryCode = mergedLoc.countryCode,
            ambientTempCelsius = mergedWeather.temperatureCelsius,
            actualBatteryTempCelsius = oldState.temperatureCelsius,
            deviceIdleState = oldState.deviceIdleState,
            isCharging = oldState.isCharging
        )

        _centralState.value = _centralState.value.copy(
            locationContext = mergedLoc,
            weatherContext = mergedWeather,
            adaptiveThermalContext = newAdaptiveThermal
        )
    }

    fun initPersistence(context: Context) {
        lastValidStatePrefs = context.getSharedPreferences("netra_last_valid_state_prefs", Context.MODE_PRIVATE)
        val prefs = lastValidStatePrefs ?: return
        if (prefs.contains("saved_battery_level") && _centralState.value.batteryLevel == null) {
            val savedLevel = prefs.getInt("saved_battery_level", -1).takeIf { it in 0..100 }
            val savedTemp = prefs.getFloat("saved_temp", -1f).takeIf { it > 0f }
            val savedVoltage = prefs.getInt("saved_voltage", -1).takeIf { it > 0 }
            val savedCurrent = prefs.getInt("saved_current", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val savedPower = prefs.getFloat("saved_power", -1f).takeIf { it >= 0f }

            // Restored persisted state is marked as LAST_KNOWN / NOT FRESH
            _centralState.value = _centralState.value.copy(
                batteryLevel = savedLevel,
                temperatureCelsius = savedTemp,
                voltageMv = savedVoltage,
                currentMa = savedCurrent,
                powerWatts = savedPower,
                isDataFresh = false, // CRITICAL: Restored state is historical / last-known, NOT fresh live!
                fieldStates = TelemetryFieldState(
                    levelStatus = if (savedLevel != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    tempStatus = if (savedTemp != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    voltageStatus = if (savedVoltage != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    currentStatus = if (savedCurrent != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE,
                    powerStatus = if (savedPower != null) FieldStatus.LAST_VALID else FieldStatus.UNAVAILABLE
                )
            )
        }
    }

    fun refreshCapabilities() {
        val oldState = _centralState.value
        val detected = capabilityRegistry?.detectAllCapabilities(
            currentMicroAmps = oldState.currentMa?.takeIf { oldState.fieldStates.currentStatus == FieldStatus.LIVE }
                ?.let { it * 1000 } ?: Int.MIN_VALUE,
            temperatureRaw = ((oldState.temperatureCelsius?.takeIf { oldState.fieldStates.tempStatus == FieldStatus.LIVE } ?: 0f) * 10).toInt(),
            voltageRaw = oldState.voltageMv?.takeIf { oldState.fieldStates.voltageStatus == FieldStatus.LIVE } ?: 0,
            connectedBluetoothCount = oldState.bluetoothDevices.count { it.isConnected }
        ) ?: return

        val policy = try {
            com.example.NetraApplication.instance.settingsRepository.settings.value.audioRoutingPolicy
        } catch (_: Exception) {
            com.example.model.AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK
        }
        val routingStatus = try {
            com.example.util.AudioRoutingInspector.inspectRouting(com.example.NetraApplication.instance, policy)
        } catch (_: Exception) {
            oldState.audioRoutingStatus
        }

        _centralState.value = _centralState.value.copy(
            capabilities = detected,
            audioRoutingStatus = routingStatus
        )
    }

    fun refreshAudioRouting(context: Context) {
        val policy = try {
            com.example.NetraApplication.instance.settingsRepository.settings.value.audioRoutingPolicy
        } catch (_: Exception) {
            com.example.model.AudioRoutingPolicy.AUTO_BT_WITH_SPEAKER_FALLBACK
        }
        val routingStatus = try {
            com.example.util.AudioRoutingInspector.inspectRouting(context, policy)
        } catch (_: Exception) {
            _centralState.value.audioRoutingStatus
        }
        _centralState.value = _centralState.value.copy(audioRoutingStatus = routingStatus)
    }

    fun updateAudioRoutingStatus(status: com.example.model.AudioRoutingStatus) {
        _centralState.value = _centralState.value.copy(audioRoutingStatus = status)
    }

    // Tracking for deduplication & sessions
    private var lastConnectedState: Boolean? = null
    private var lastChargingState: Boolean? = null
    private var lastSpeedCategory: CanonicalChargingSpeed? = null
    private var lastBatteryLevelBoundary: Int? = null

    private var lastThermalWarningState = false
    private var lastCriticalOverheatState = false
    private var isCriticalThermalActiveState = false
    private var isLowBatteryControlActiveState = false
    private var targetBrightnessPercentState: Int? = null

    private var chargerConnectedAt: Long? = null
    private var chargingStartedAt: Long? = null
    private var chargingStoppedAt: Long? = null
    private var chargerDisconnectedAt: Long? = null
    private var dischargingStartedAt: Long? = null
    private var lastDischargingStatus = false

    // ETA evidence is separate from retained display values and never survives a session boundary.
    private val sessionEtaEstimator = SessionEtaEstimator()

    suspend fun processRawInput(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        temperatureRaw: Int,
        voltage: Int,
        currentMicroAmps: Int,
        bluetoothConnected: Boolean?,
        bluetoothBattery: Int?,
        source: String = "BatteryMonitorService"
    ) {
        val t0Nanos = System.nanoTime()
        val eventsToEmit = mutableListOf<NetraCentralEvent>()
        val newState = telemetryMutex.withLock {
            val now = telemetryClock()
            val oldState = _centralState.value

            if (chargerConnectedAt == null) chargerConnectedAt = oldState.chargerConnectedAt
            if (chargingStartedAt == null) chargingStartedAt = oldState.chargingStartedAt
            if (chargingStoppedAt == null) chargingStoppedAt = oldState.chargingStoppedAt
            if (chargerDisconnectedAt == null) chargerDisconnectedAt = oldState.chargerDisconnectedAt
            if (dischargingStartedAt == null) dischargingStartedAt = oldState.dischargingStartedAt
            if (lastConnectedState == null) lastConnectedState = oldState.isChargerConnected
            if (lastChargingState == null) lastChargingState = oldState.isCharging
            lastDischargingStatus = oldState.isCharging == false && (oldState.dischargingStartedAt != null)

            // 1. Validation & Normalization (No fake fallbacks)
            val validatedLevel = if (level in 0..scale && scale > 0) (level.toLong() * 100 / scale).toInt() else null
            val isCharging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }

            val isConnected = when {
                plugged == 0 -> false
                plugged > 0 -> true
                isCharging == true -> true
                else -> null
            }

            val pluggedType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> CanonicalPluggedType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> CanonicalPluggedType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> CanonicalPluggedType.WIRELESS
                0 -> CanonicalPluggedType.NONE
                else -> if (isCharging == true) CanonicalPluggedType.OTHER else CanonicalPluggedType.UNKNOWN
            }

            val tempCelsius = if (temperatureRaw > 0) temperatureRaw / 10.0f else null
            val voltageMv = if (voltage > 0) voltage else null

            // BatteryManager.BATTERY_PROPERTY_CURRENT_NOW is specified in microamps.
            val currentMa = if (currentMicroAmps != Int.MIN_VALUE) {
                currentMicroAmps / 1000
            } else null

            val t1Nanos = System.nanoTime()

            // 2. Strict Field-Level Last-Valid-Value Retention Mechanism
            // Missing, null, or invalid fields do NOT overwrite valid existing values.
            val mergedLevel = validatedLevel ?: oldState.batteryLevel
            val mergedIsCharging = isCharging ?: oldState.isCharging
            val mergedIsConnected = isConnected ?: oldState.isChargerConnected
            val mergedPluggedType = if (plugged > 0 || plugged == 0) pluggedType else (oldState.pluggedType ?: pluggedType)
            val mergedTempCelsius = tempCelsius ?: oldState.temperatureCelsius
            val mergedVoltageMv = voltageMv ?: oldState.voltageMv
            val mergedCurrentMa = currentMa ?: oldState.currentMa

            // Central ChargingSpeedEngine calculation using raw incoming power exclusively
            val speedResult = chargingSpeedEngine.calculate(isCharging, voltageMv, currentMa)
            val mergedRawPower = speedResult.rawPowerWatts ?: oldState.powerWatts
            val mergedConsumption = speedResult.consumptionPowerWatts ?: oldState.consumptionPowerWatts
            val mergedSpeed = if (mergedIsCharging == true) {
                if (speedResult.speedCategory != CanonicalChargingSpeed.UNAVAILABLE) speedResult.speedCategory else oldState.chargingSpeed
            } else {
                CanonicalChargingSpeed.UNAVAILABLE
            }
            val mergedAnnouncementSpeed = speedResult.announcementCategory

            // Calculate precise FieldStatus for each telemetry parameter
            val fieldStates = TelemetryFieldState(
                levelStatus = when {
                    validatedLevel != null -> FieldStatus.LIVE
                    oldState.batteryLevel != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                tempStatus = when {
                    tempCelsius != null -> FieldStatus.LIVE
                    oldState.temperatureCelsius != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                voltageStatus = when {
                    voltageMv != null -> FieldStatus.LIVE
                    oldState.voltageMv != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                currentStatus = when {
                    currentMa != null -> FieldStatus.LIVE
                    oldState.currentMa != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                powerStatus = when {
                    speedResult.rawPowerWatts != null -> FieldStatus.LIVE
                    oldState.powerWatts != null -> FieldStatus.LAST_VALID
                    else -> FieldStatus.UNAVAILABLE
                },
                levelObservedAt = if (validatedLevel != null) now else oldState.fieldStates.levelObservedAt,
                tempObservedAt = if (tempCelsius != null) now else oldState.fieldStates.tempObservedAt,
                voltageObservedAt = if (voltageMv != null) now else oldState.fieldStates.voltageObservedAt,
                currentObservedAt = if (currentMa != null) now else oldState.fieldStates.currentObservedAt,
                powerObservedAt = if (speedResult.rawPowerWatts != null) now else oldState.fieldStates.powerObservedAt
            )

            // Fast in-memory capability update (no blocking Binder IPC calls in critical path)
            val detectedCapabilities = if (oldState.capabilities.isNotEmpty()) {
                val updated = oldState.capabilities.toMutableMap()
                val currentAvailable = currentMicroAmps != Int.MIN_VALUE
                val voltageAvailable = voltage > 0
                val powerAvailable = currentAvailable && voltageAvailable
                updated[CapabilityType.BATTERY_CURRENT] = if (currentAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
                updated[CapabilityType.BATTERY_TEMPERATURE] = if (temperatureRaw > 0) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
                updated[CapabilityType.BATTERY_VOLTAGE] = if (voltageAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
                val powerStatus = if (powerAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
                updated[CapabilityType.BATTERY_POWER_CALCULATION] = powerStatus
                updated[CapabilityType.CHARGING_SPEED_CALCULATION] = powerStatus
                updated[CapabilityType.FAST_CHARGING_DETECTION] = powerStatus
                updated
            } else {
                capabilityRegistry?.detectAllCapabilities(
                    currentMicroAmps = currentMicroAmps,
                    temperatureRaw = temperatureRaw,
                    voltageRaw = voltage,
                    connectedBluetoothCount = oldState.bluetoothDevices.count { it.isConnected }
                ) ?: oldState.capabilities
            }

            val mergedBluetoothConnected = bluetoothConnected ?: oldState.bluetoothConnected
            val mergedBluetoothBattery = bluetoothBattery ?: oldState.bluetoothBatteryPercent

            // Session Timestamp tracking
            if (mergedIsConnected != null && mergedIsConnected != lastConnectedState) {
                if (mergedIsConnected) {
                    chargerConnectedAt = now
                    chargerDisconnectedAt = null
                } else {
                    chargerDisconnectedAt = now
                    chargerConnectedAt = null
                    chargingStartedAt = null
                    chargingStoppedAt = null
                }
            }
            if (mergedIsCharging != null && mergedIsCharging != lastChargingState) {
                if (mergedIsCharging) {
                    chargingStartedAt = now
                    chargingStoppedAt = null
                    dischargingStartedAt = null
                } else {
                    chargingStoppedAt = now
                }
            }

            val isDischarging = (status == BatteryManager.BATTERY_STATUS_DISCHARGING) || (mergedIsCharging == false && mergedIsConnected == false)
            val wasDischarging = lastDischargingStatus
            if (isDischarging && !wasDischarging) {
                dischargingStartedAt = now
            }
            lastDischargingStatus = isDischarging

            // Only raw valid levels/status may establish current-session progression.
            // Invalid input clears ETA evidence while last-valid display telemetry stays intact.
            val eta = sessionEtaEstimator.observe(validatedLevel, status, plugged, now)
            val mergedChargingEta = eta.chargingMinutes
            val mergedDischargingEta = eta.dischargingMinutes

            // 2. Deduplication & Event Generation
            if (isConnected != null && isConnected != lastConnectedState) {
                val previousConnectedState = lastConnectedState
                lastConnectedState = isConnected
                val eventType = if (isConnected) NetraEventType.CHARGER_CONNECTED else NetraEventType.CHARGER_DISCONNECTED
                eventsToEmit.add(
                    NetraCentralEvent(
                        eventId = "event_${eventType}_$now",
                        eventType = eventType,
                        timestamp = now,
                        previousValue = previousConnectedState?.toString(),
                        newValue = isConnected.toString(),
                        source = source
                    )
                )
            }

            if (isCharging != null && isCharging != lastChargingState) {
                val previousChargingState = lastChargingState
                lastChargingState = isCharging
                val eventType = if (isCharging) {
                    NetraEventType.CHARGING_STARTED
                } else {
                    NetraEventType.CHARGING_STOPPED
                }
                eventsToEmit.add(
                    NetraCentralEvent(
                        eventId = "event_${eventType}_$now",
                        eventType = eventType,
                        timestamp = now,
                        previousValue = previousChargingState?.toString(),
                        newValue = isCharging.toString(),
                        source = source
                    )
                )
            }

            if (isDischarging && !wasDischarging) {
                eventsToEmit.add(
                    NetraCentralEvent(
                        eventId = "event_DISCHARGING_STARTED_$now",
                        eventType = NetraEventType.DISCHARGING_STARTED,
                        timestamp = now,
                        previousValue = wasDischarging.toString(),
                        newValue = isDischarging.toString(),
                        source = source
                    )
                )
            }

            if (mergedAnnouncementSpeed != CanonicalChargingSpeed.UNAVAILABLE) {
                if (lastSpeedCategory != mergedAnnouncementSpeed) {
                    val prev = lastSpeedCategory?.name ?: "UNAVAILABLE"
                    lastSpeedCategory = mergedAnnouncementSpeed
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_speed_change_$now",
                            eventType = NetraEventType.SPEED_CHANGED,
                            timestamp = now,
                            previousValue = prev,
                            newValue = mergedAnnouncementSpeed.name,
                            source = source
                        )
                    )
                }
            }

            if (mergedLevel != null) {
                val boundary = (mergedLevel / 5) * 5
                if (lastBatteryLevelBoundary != boundary) {
                    val previousBoundary = lastBatteryLevelBoundary
                    lastBatteryLevelBoundary = boundary
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_battery_boundary_${boundary}_$now",
                            eventType = NetraEventType.BATTERY_LEVEL_CROSSED,
                            timestamp = now,
                            previousValue = previousBoundary?.toString(),
                            newValue = boundary.toString(),
                            source = source
                        )
                    )
                }
            }

            // Thermal warning/critical/recovered event generation
            if (mergedTempCelsius != null) {
                val isWarning = mergedTempCelsius >= 40.0f
                val isCritical = mergedTempCelsius >= 45.0f

                if (isCritical && !lastCriticalOverheatState) {
                    lastCriticalOverheatState = true
                    lastThermalWarningState = true
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_thermal_crit_$now",
                            eventType = NetraEventType.THERMAL_CRITICAL,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                } else if (isWarning && !lastThermalWarningState && !isCritical) {
                    lastThermalWarningState = true
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_thermal_warn_$now",
                            eventType = NetraEventType.THERMAL_WARNING,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                } else if (!isWarning && !isCritical && (lastThermalWarningState || lastCriticalOverheatState)) {
                    lastThermalWarningState = false
                    lastCriticalOverheatState = false
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_thermal_rec_$now",
                            eventType = NetraEventType.THERMAL_RECOVERED,
                            timestamp = now,
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                }
            }

            val canonicalChargerState = when {
                mergedIsConnected == true && mergedIsCharging == true -> com.example.model.CanonicalChargerState.CHARGER_CONNECTED_CHARGING
                mergedIsConnected == true && mergedIsCharging == false -> com.example.model.CanonicalChargerState.CHARGER_CONNECTED_NOT_CHARGING
                mergedIsConnected == false && isDischarging -> com.example.model.CanonicalChargerState.DISCHARGING
                mergedIsConnected == false -> com.example.model.CanonicalChargerState.CHARGER_DISCONNECTED
                else -> com.example.model.CanonicalChargerState.UNKNOWN
            }

            if (mergedTempCelsius != null) {
                if (mergedTempCelsius > 40.0f && !isCriticalThermalActiveState) {
                    isCriticalThermalActiveState = true
                    targetBrightnessPercentState = 10
                    thermalInvestigator?.startInvestigation()
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_thermal_prot_start_$now",
                            eventType = NetraEventType.THERMAL_PROTECTION_STARTED,
                            timestamp = now,
                            previousValue = "NORMAL",
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                } else if (mergedTempCelsius <= 35.0f && isCriticalThermalActiveState) {
                    isCriticalThermalActiveState = false
                    thermalInvestigator?.stopInvestigation()
                    if (!isLowBatteryControlActiveState) {
                        targetBrightnessPercentState = null
                    }
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_thermal_prot_rec_$now",
                            eventType = NetraEventType.THERMAL_PROTECTION_RECOVERED,
                            timestamp = now,
                            previousValue = "CRITICAL",
                            newValue = mergedTempCelsius.toString(),
                            source = source
                        )
                    )
                }
            }

            if (mergedLevel != null) {
                if (mergedLevel <= 30 && mergedIsCharging != true && !isLowBatteryControlActiveState) {
                    isLowBatteryControlActiveState = true
                    targetBrightnessPercentState = 10
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_low_bat_prot_start_$now",
                            eventType = NetraEventType.LOW_BATTERY_PROTECTION_STARTED,
                            timestamp = now,
                            previousValue = "NORMAL",
                            newValue = mergedLevel.toString(),
                            source = source
                        )
                    )
                } else if (mergedLevel >= 35 && isLowBatteryControlActiveState) {
                    isLowBatteryControlActiveState = false
                    if (!isCriticalThermalActiveState) {
                        targetBrightnessPercentState = null
                    }
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_low_bat_prot_rec_$now",
                            eventType = NetraEventType.LOW_BATTERY_PROTECTION_RECOVERED,
                            timestamp = now,
                            previousValue = "LOW_BATTERY",
                            newValue = mergedLevel.toString(),
                            source = source
                        )
                    )
                }
            }

            val diagnosisResult = if (mergedTempCelsius != null) {
                thermalInvestigator?.diagnoseThermalCause(
                    batteryTempCelsius = mergedTempCelsius,
                    weatherAmbientTempCelsius = oldState.weatherContext.temperatureCelsius,
                    isCharging = mergedIsCharging == true,
                    isHeavyLoad = (mergedCurrentMa != null && kotlin.math.abs(mergedCurrentMa) > 500)
                )
            } else null

            val thermalDiagnosis = diagnosisResult?.message ?: if (isCriticalThermalActiveState) "Thermal stress active (>40°C)." else null
            val envDiagnosis = diagnosisResult?.state ?: com.example.model.EnvironmentalHeatDiagnosis.NORMAL_ENVIRONMENTAL_CONTEXT

            val deviceIdleState = when {
                mergedIsCharging == true -> com.example.model.DeviceIdleState.CHARGING_IDLE
                mergedCurrentMa != null && kotlin.math.abs(mergedCurrentMa) > 500 -> com.example.model.DeviceIdleState.ACTIVE
                else -> com.example.model.DeviceIdleState.IDLE
            }

            val adaptiveThermal = ClimateBaselineEngine.computeAdaptiveThermalContext(
                countryCode = oldState.locationContext.countryCode,
                ambientTempCelsius = oldState.weatherContext.temperatureCelsius,
                actualBatteryTempCelsius = mergedTempCelsius,
                deviceIdleState = deviceIdleState,
                isCharging = mergedIsCharging
            )

            if (adaptiveThermal.warningCategory == com.example.model.AdaptiveWarningCategory.THERMAL_ANOMALY &&
                oldState.adaptiveThermalContext.warningCategory != com.example.model.AdaptiveWarningCategory.THERMAL_ANOMALY) {
                eventsToEmit.add(
                    NetraCentralEvent(
                        eventId = "event_thermal_anomaly_$now",
                        eventType = NetraEventType.THERMAL_ANOMALY_DETECTED,
                        timestamp = now,
                        previousValue = oldState.adaptiveThermalContext.warningCategory.name,
                        newValue = mergedTempCelsius.toString(),
                        source = source
                    )
                )
            }

            val t2Nanos = System.nanoTime()
            val valDurationMs = (t1Nanos - t0Nanos) / 1_000_000f
            val updateDurationMs = (t2Nanos - t1Nanos) / 1_000_000f
            val totalProcessingMs = (t2Nanos - t0Nanos) / 1_000_000f

            val latencyMetrics = com.example.model.PipelineLatencyMetrics(
                t0ReceivedNanos = t0Nanos,
                t1ValidatedNanos = t1Nanos,
                t2StateUpdatedNanos = t2Nanos,
                t3EmittedNanos = t2Nanos,
                validationDurationMs = valDurationMs,
                updateDurationMs = updateDurationMs,
                totalProcessingMs = totalProcessingMs,
                meetsBudget = totalProcessingMs <= 100f
            )

            val updatedState = NetraCentralState(
                batteryLevel = mergedLevel,
                isCharging = mergedIsCharging,
                isChargerConnected = mergedIsConnected,
                canonicalChargerState = canonicalChargerState,
                chargerConnectedAt = chargerConnectedAt,
                chargingStartedAt = chargingStartedAt,
                chargingStoppedAt = chargingStoppedAt,
                chargerDisconnectedAt = chargerDisconnectedAt,
                dischargingStartedAt = dischargingStartedAt,
                pluggedType = mergedPluggedType,
                temperatureCelsius = mergedTempCelsius,
                voltageMv = mergedVoltageMv,
                currentMa = mergedCurrentMa,
                powerWatts = mergedRawPower,
                netPowerWatts = null,
                consumptionPowerWatts = mergedConsumption,
                chargingSpeed = mergedSpeed,
                announcementSpeed = mergedAnnouncementSpeed,
                bluetoothConnected = mergedBluetoothConnected,
                bluetoothBatteryPercent = mergedBluetoothBattery,
                bluetoothDevices = oldState.bluetoothDevices,
                bluetoothHistory = oldState.bluetoothHistory,
                lastUpdateTimestamp = if (validatedLevel != null || tempCelsius != null || voltageMv != null || currentMa != null) now else oldState.lastUpdateTimestamp,
                isDataFresh = validatedLevel != null && isCharging != null,
                fieldStates = fieldStates,
                capabilities = detectedCapabilities,
                chargingEtaMinutes = mergedChargingEta,
                dischargingEtaMinutes = mergedDischargingEta,
                mediaState = oldState.mediaState,
                isMediaControlAvailable = oldState.isMediaControlAvailable,
                mediaPausedByNethra = oldState.mediaPausedByNethra,
                isNightProtectionActive = oldState.isNightProtectionActive,
                isCriticalThermalActive = isCriticalThermalActiveState,
                isLowBatteryControlActive = isLowBatteryControlActiveState,
                targetBrightnessPercent = targetBrightnessPercentState,
                thermalCauseDiagnosis = thermalDiagnosis,
                environmentalDiagnosis = envDiagnosis,
                locationContext = oldState.locationContext,
                weatherContext = oldState.weatherContext,
                deviceIdleState = deviceIdleState,
                adaptiveThermalContext = adaptiveThermal,
                pipelineLatency = latencyMetrics
            )

            // Publish state IMMEDIATELY (single atomic update)
            _centralState.value = updatedState
            updatedState
        }

        // Non-suspending event delivery to collectors
        for (event in eventsToEmit) {
            _centralEvents.tryEmit(event)
        }

        // Offload disk persistence to backgroundScope strictly AFTER state publication
        backgroundScope.launch {
            try {
                lastValidStatePrefs?.edit()?.apply {
                    if (newState.batteryLevel != null) putInt("saved_battery_level", newState.batteryLevel)
                    if (newState.temperatureCelsius != null) putFloat("saved_temp", newState.temperatureCelsius)
                    if (newState.voltageMv != null) putInt("saved_voltage", newState.voltageMv)
                    if (newState.currentMa != null) putInt("saved_current", newState.currentMa)
                    if (newState.powerWatts != null) putFloat("saved_power", newState.powerWatts)
                    apply()
                }
            } catch (_: Exception) {}
        }
    }

    /** A running service or Bluetooth update is not a new battery observation. */
    suspend fun expireTelemetryFreshness() = telemetryMutex.withLock {
        val state = _centralState.value
        val now = telemetryClock()
        fun aged(status: FieldStatus, observedAt: Long): FieldStatus =
            if (status == FieldStatus.LIVE && (observedAt <= 0L || now < observedAt || now - observedAt > 120_000L))
                FieldStatus.LAST_VALID else status
        val f = state.fieldStates
        val agedFields = f.copy(
            levelStatus = aged(f.levelStatus, f.levelObservedAt),
            tempStatus = aged(f.tempStatus, f.tempObservedAt),
            voltageStatus = aged(f.voltageStatus, f.voltageObservedAt),
            currentStatus = aged(f.currentStatus, f.currentObservedAt),
            powerStatus = aged(f.powerStatus, f.powerObservedAt)
        )
        val fresh = state.isDataFresh && agedFields.levelStatus == FieldStatus.LIVE
        val agedCapabilities = state.capabilities.toMutableMap().apply {
            fun revoke(type: CapabilityType, status: FieldStatus) {
                if (status != FieldStatus.LIVE && this[type] == CapabilityStatus.AVAILABLE) {
                    this[type] = CapabilityStatus.UNAVAILABLE
                }
            }
            revoke(CapabilityType.BATTERY_TEMPERATURE, agedFields.tempStatus)
            revoke(CapabilityType.BATTERY_VOLTAGE, agedFields.voltageStatus)
            revoke(CapabilityType.BATTERY_CURRENT, agedFields.currentStatus)
            val powerInputsLive = agedFields.currentStatus == FieldStatus.LIVE &&
                agedFields.voltageStatus == FieldStatus.LIVE
            if (!powerInputsLive) {
                for (type in listOf(CapabilityType.BATTERY_POWER_CALCULATION,
                    CapabilityType.CHARGING_SPEED_CALCULATION, CapabilityType.FAST_CHARGING_DETECTION)) {
                    if (this[type] == CapabilityStatus.AVAILABLE) this[type] = CapabilityStatus.UNAVAILABLE
                }
            }
        }
        _centralState.value = state.copy(
            fieldStates = agedFields, isDataFresh = fresh, capabilities = agedCapabilities,
            chargingEtaMinutes = if (fresh) state.chargingEtaMinutes else null,
            dischargingEtaMinutes = if (fresh) state.dischargingEtaMinutes else null,
            announcementSpeed = if (agedFields.powerStatus == FieldStatus.LIVE) state.announcementSpeed else CanonicalChargingSpeed.UNAVAILABLE
        )
    }

    suspend fun processBluetoothDevices(devices: List<com.example.model.BluetoothDeviceItem>, source: String = "BluetoothHelper") {
        val eventsToEmit = mutableListOf<NetraCentralEvent>()
        bluetoothMutex.withLock {
            val now = System.currentTimeMillis()
            val oldState = _centralState.value

            val mergedDevices = devices.map { device ->
                val deviceKey = device.address.ifBlank { device.name }
                val oldDevice = oldState.bluetoothDevices.find { (it.address.ifBlank { it.name }) == deviceKey }

                // Carry forward last valid battery level if temporarily null
                val mergedBattery = device.batteryPercent ?: oldDevice?.batteryPercent
                device.copy(batteryPercent = mergedBattery)
            }

            // Connection and Disconnection checks
            for (device in mergedDevices) {
                val deviceKey = device.address.ifBlank { device.name }
                val oldDevice = oldState.bluetoothDevices.find { (it.address.ifBlank { it.name }) == deviceKey }

                if (device.isConnected && (oldDevice == null || !oldDevice.isConnected)) {
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_bt_conn_${deviceKey}_$now",
                            eventType = NetraEventType.BLUETOOTH_CONNECTED,
                            timestamp = now,
                            newValue = device.name,
                            source = source
                        )
                    )
                }

                // Check battery boundary crossing for Bluetooth (10% increments)
                if (device.isConnected && device.batteryPercent != null) {
                    val currentPercent = device.batteryPercent
                    val oldPercent = oldDevice?.batteryPercent
                    if (currentPercent % 10 == 0 && (oldPercent == null || oldPercent != currentPercent)) {
                        eventsToEmit.add(
                            NetraCentralEvent(
                                eventId = "event_bt_bat_${deviceKey}_${currentPercent}_$now",
                                eventType = NetraEventType.BLUETOOTH_BATTERY_BOUNDARY,
                                timestamp = now,
                                previousValue = device.name,
                                newValue = currentPercent.toString(),
                                source = source
                            )
                        )
                    }
                }
            }

            for (oldDevice in oldState.bluetoothDevices) {
                val deviceKey = oldDevice.address.ifBlank { oldDevice.name }
                val newDevice = mergedDevices.find { (it.address.ifBlank { it.name }) == deviceKey }
                if (oldDevice.isConnected && (newDevice == null || !newDevice.isConnected)) {
                    eventsToEmit.add(
                        NetraCentralEvent(
                            eventId = "event_bt_disc_${deviceKey}_$now",
                            eventType = NetraEventType.BLUETOOTH_DISCONNECTED,
                            timestamp = now,
                            newValue = oldDevice.name,
                            source = source
                        )
                    )
                }
            }

            // Process Persistent Bluetooth History using in-memory cache (no blocking disk I/O in critical path)
            val historyMap = mutableMapOf<String, com.example.model.BluetoothDeviceItem>()
            val existingHistory = inMemoryBluetoothHistory ?: (if (oldState.bluetoothHistory.isNotEmpty()) oldState.bluetoothHistory else loadBluetoothHistory()).toMutableList().also { inMemoryBluetoothHistory = it }
            
            existingHistory.forEach { dev ->
                historyMap[dev.address.ifBlank { dev.name }] = dev
            }

            // Add current connected devices
            mergedDevices.forEach { dev ->
                val key = dev.address.ifBlank { dev.name }
                historyMap[key] = dev.copy(isConnected = false)
            }

            val updatedHistoryList = historyMap.values.toList()
            inMemoryBluetoothHistory = updatedHistoryList.toMutableList()

            val bluetoothConnected = mergedDevices.any { it.isConnected }
            val highestBattery = mergedDevices.filter { it.isConnected && it.batteryPercent != null }
                .maxOfOrNull { it.batteryPercent!! }

            val settings = com.example.NetraApplication.instance.settingsRepository.settings.value
            val isNightActive = calculateIsNightProtectionActive(settings)

            val newState = oldState.copy(
                bluetoothConnected = bluetoothConnected,
                bluetoothBatteryPercent = highestBattery,
                bluetoothDevices = mergedDevices,
                bluetoothHistory = updatedHistoryList,
                isNightProtectionActive = isNightActive
            )

            // Publish state immediately!
            _centralState.value = newState

            // Offload disk persistence to backgroundScope without delaying live UI
            backgroundScope.launch {
                saveBluetoothHistory(updatedHistoryList)
            }
        }

        // Emit events non-suspending
        for (event in eventsToEmit) {
            _centralEvents.tryEmit(event)
        }
    }

    private fun saveBluetoothHistory(historyList: List<com.example.model.BluetoothDeviceItem>) {
        try {
            val context = com.example.NetraApplication.instance.applicationContext
            val prefs = context.getSharedPreferences("netra_bluetooth_history_prefs", Context.MODE_PRIVATE)
            val array = org.json.JSONArray()
            for (device in historyList) {
                val obj = org.json.JSONObject()
                obj.put("name", device.name)
                obj.put("address", device.address)
                obj.put("isConnected", false)
                obj.put("isPaired", device.isPaired)
                obj.put("deviceType", device.deviceType)
                obj.put("batteryPercent", device.batteryPercent ?: -1)
                obj.put("profile", device.profile)
                array.put(obj)
            }
            prefs.edit().putString("bluetooth_history", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadBluetoothHistory(): List<com.example.model.BluetoothDeviceItem> {
        val list = mutableListOf<com.example.model.BluetoothDeviceItem>()
        try {
            val context = com.example.NetraApplication.instance.applicationContext
            val prefs = context.getSharedPreferences("netra_bluetooth_history_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("bluetooth_history", null) ?: return emptyList()
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val batteryVal = obj.optInt("batteryPercent", -1)
                list.add(
                    com.example.model.BluetoothDeviceItem(
                        name = obj.getString("name"),
                        address = obj.getString("address"),
                        isConnected = false,
                        isPaired = obj.optBoolean("isPaired", true),
                        deviceType = obj.getString("deviceType"),
                        batteryPercent = if (batteryVal >= 0) batteryVal else null,
                        profile = obj.optString("profile", "A2DP / HFP")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun calculateIsNightProtectionActive(settings: com.example.data.repository.SentinelSettings): Boolean {
        if (!settings.nightProtectionEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val start = settings.nightStartHour
        val end = settings.nightEndHour
        return if (start == end) {
            false
        } else if (start > end) {
            currentHour >= start || currentHour < end
        } else {
            currentHour in start until end
        }
    }

    fun refreshNightProtectionStateSynchronously() {
        val settings = com.example.NetraApplication.instance.settingsRepository.settings.value
        val isNightActive = calculateIsNightProtectionActive(settings)
        val oldState = _centralState.value
        if (oldState.isNightProtectionActive != isNightActive) {
            _centralState.value = oldState.copy(isNightProtectionActive = isNightActive)
        }
    }

    suspend fun refreshNightProtectionState() {
        telemetryMutex.withLock {
            refreshNightProtectionStateSynchronously()
        }
    }

    private var mediaPlaybackController: com.example.util.MediaPlaybackController? = null

    private fun getMediaController(context: Context): com.example.util.MediaPlaybackController {
        if (mediaPlaybackController == null) {
            mediaPlaybackController = com.example.util.MediaPlaybackController(context.applicationContext)
        }
        return mediaPlaybackController!!
    }

    suspend fun updateMediaState(state: com.example.model.CanonicalMediaState) {
        mediaMutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(mediaState = state)
        }
    }

    suspend fun updateMediaControlAvailable(available: Boolean) {
        mediaMutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(isMediaControlAvailable = available)
        }
    }

    suspend fun setMediaPausedByNethra(paused: Boolean) {
        mediaMutex.withLock {
            val oldState = _centralState.value
            _centralState.value = oldState.copy(mediaPausedByNethra = paused)
        }
    }

    suspend fun requestMediaPause(context: Context): Boolean {
        return mediaMutex.withLock {
            val oldState = _centralState.value
            if (!oldState.isMediaControlAvailable || oldState.mediaState == com.example.model.CanonicalMediaState.UNSUPPORTED) {
                return false
            }

            val controller = getMediaController(context)
            val isPlaying = controller.isMediaPlaying()
            
            if (isPlaying) {
                controller.prepareForAnnouncement()
                _centralState.value = oldState.copy(
                    mediaState = com.example.model.CanonicalMediaState.PAUSED,
                    mediaPausedByNethra = true
                )
                true
            } else {
                _centralState.value = oldState.copy(
                    mediaState = if (controller.isMediaPlaying()) com.example.model.CanonicalMediaState.PLAYING else com.example.model.CanonicalMediaState.PAUSED
                )
                false
            }
        }
    }

    suspend fun requestMediaResume(context: Context): Boolean {
        return mediaMutex.withLock {
            val oldState = _centralState.value
            if (oldState.mediaPausedByNethra) {
                val controller = getMediaController(context)
                controller.restoreAfterAnnouncement()
                _centralState.value = oldState.copy(
                    mediaState = com.example.model.CanonicalMediaState.PLAYING,
                    mediaPausedByNethra = false
                )
                true
            } else {
                false
            }
        }
    }
}
