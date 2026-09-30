# Battery Sentinel Pro Netra

**Ultra-Low Power 24/7 Intelligent Battery, Thermal Sentinel & Autonomous Diagnostic Engine**

Battery Sentinel Pro Netra is an ultra-low-power, event-driven Android battery sentinel built with modern Jetpack Compose, Kotlin Coroutines & Flow, and SQLite Room persistence. The application operates under an absolute priority hierarchy: **Ultra-Low Power First, Device Safety Second, 24/7 Reliability Third, Automatic Recovery Fourth, and Performance/Features Last**.

---

## 🏛️ System Architecture: Central Unit Authority

All device telemetry, hardware sensor reads, battery broadcasts, Bluetooth peripherals, geolocation, and weather context flow through a single unified authority:

```
Android APIs / Battery / Sensors / Bluetooth / Location / Weather
                             ↓
                    Existing Collectors
                             ↓
                     NETRA CENTRAL UNIT
                             ↓
             Validate → Normalize → Deduplicate
                             ↓
                  Field-Level Last-Valid Merge
                             ↓
                   Canonical State & Events
                             ↓
         Protection / Announcement Engine / UI Screens
                             ↓
               Room SQLite Logs / Telemetry Cache
```

- **Single Source of Truth**: `NetraCentralDataCenter` produces immutable `NetraCentralState` flows and discrete `NetraCentralEvent` emissions. No UI screen or sub-service calculates independent battery, thermal, or permission states.
- **Pipeline Latency**: Critical telemetry ingestion and emission completes well within the <=100ms budget without artificial delays or synthetic polling loops.

---

## 🛡️ Protection & Recovery Matrix

| Protection Subsystem | Entry Trigger | Recovery Trigger | Canonical Action |
| :--- | :--- | :--- | :--- |
| **Critical Thermal Protection** | Battery Temp > 40.0°C | Battery Temp <= 35.0°C | Dims display brightness toward 10%, starts `ThermalCauseInvestigator`, emits `THERMAL_PROTECTION_STARTED`, announces critical voice warning. |
| **Low-Battery Protection** | Battery Level <= 30% (Discharging) | Battery Level >= 35% | Dims display brightness toward 10%, emits `LOW_BATTERY_PROTECTION_STARTED`. *Note: Connecting a charger does NOT recover protection while SoC is <35%.* |
| **Protection Coexistence** | Both Active | Independent Recovery | Shared actions (e.g., brightness dimming) execute once. Thermal recovers at <=35°C independently of SoC; Low-Battery recovers at >=35% independently of temperature. |

---

## ⚡ Charging & Telemetry Model

- **Raw Incoming Power**: Power is computed exclusively from raw incoming electrical power (V x I). Device self-consumption is never subtracted from the charging power classification.
- **Speed Classification Tiers**:
  - **Slow Charging**: < 5.0W
  - **Normal Charging**: 5.0W <= P < 10.0W
  - **Fast Charging**: 10.0W <= P <= 20.0W
  - **Ultra Fast Charging**: > 20.0W
- **Canonical Charger States**:
  - `CHARGER_CONNECTED_CHARGING`
  - `CHARGER_CONNECTED_NOT_CHARGING` (e.g., connected to USB host without confirmed charge current)
  - `CHARGER_DISCONNECTED`
  - `DISCHARGING`
  - `UNKNOWN`
- **Progression-Based ETA**: Charging and discharging time-to-full/empty estimates are computed solely from observed linear SoC progression over time (>=2 samples, >=2 minutes). Returns `null` when telemetry history is insufficient.

---

## 🌍 Geo-Climate Adaptive Thermal Baseline

- **Hierarchical Country Detection**: `LocationCountryResolver` determines country and region using Telephony/SIM ISO -> Device Locale -> Coarse Geocoder when permissions are available. Works internationally without hardcoded regions.
- **Weather Context Engine**: `WeatherContextEngine` integrates Open-Meteo REST API data with a 30-minute power-conserving cache and fallback to onboard hardware ambient temperature sensors (`Sensor.TYPE_AMBIENT_TEMPERATURE`).
- **Climate Baseline Engine**: Computes expected idle battery ranges for regional climate profiles (e.g., India Peak Summer: 36–41°C, India Monsoon/Moderate: 31–35°C, Continental, Tropical, Cold). Distinguishes high ambient heat from app thermal anomalies without overriding the >40°C critical safety threshold.
- **Environmental Heat Diagnosis States**:
  - `NORMAL_ENVIRONMENTAL_CONTEXT`
  - `ENVIRONMENTAL_HEAT_LIKELY`
  - `INTERNAL_HEAT_LIKELY`
  - `MIXED_HEAT_CONTEXT`
  - `INSUFFICIENT_SENSOR_DATA`

---

## 🔊 Centralized Announcement & Media Policy

- **Single Engine & Queue**: All voice alerts pass through `AnnouncementEngine` utilizing a priority queue (`CRITICAL_THERMAL` -> `CHARGER_STATE` -> `BLUETOOTH_STATE` -> `CHARGING_SPEED` -> `PHONE_BATTERY` -> `BLUETOOTH_BATTERY` -> `INFORMATIONAL`).
- **Night Protection**: From **23:00 to 06:00**, routine battery, charging speed, and Bluetooth announcements are suppressed. Critical thermal and safety alerts remain active.
- **Media Playback Integration**: `MediaPlaybackController` pauses active media before critical announcements and resumes playback **only if Nethra initiated the pause**. Pre-existing paused media remains paused.

---

## 📱 Navigation & Screen Structure

The app contains five primary navigation destinations:

1. **Status (Home)**: Live circular battery gauge, AI health intelligence, dynamic power profile selector, 1-hour thermal sparkline, Geo-Climate Adaptive card, Charging Intelligence, and Permission overview.
2. **Battery**: Detailed charging vs discharging telemetry, charging speed tier analysis, and recent charging session history.
3. **Monitoring**:
   - *Hardware Sub-tab*: Live sensor metrics, storage/cache manager, central capability registry, and Geo-Climate context.
   - *App Drain Sub-tab*: Real Android Usage Access statistics (`PACKAGE_USAGE_STATS`) per application.
   - *Event Logs Sub-tab*: Categorized SQLite activity logs of meaningful state transitions.
4. **Devices**: Live connected Bluetooth peripherals and paired device history with last-valid battery retention.
5. **Settings**: User configurable thermal alert thresholds, target charge alarms, 24/7 OEM sleep exemption, CSV/PDF report generators, and database maintenance.

---

## 📋 Feature Verification & Implementation Status

| Feature / Subsystem | Implementation Status | Verification Notes |
| :--- | :--- | :--- |
| **Five-Tab Navigation** | **Verified** | Exactly 5 canonical tabs (`HOME`, `BATTERY`, `MONITORING`, `DEVICES`, `SETTINGS`). |
| **Central Unit Authority** | **Verified** | `NetraCentralDataCenter` validates, normalizes, and emits single-source state. |
| **Field-Level Last-Valid Retention**| **Verified** | Telemetry and weather fields retain last valid values during partial sample drops. |
| **Critical Thermal Protection (>40°C)** | **Verified** | Dims brightness to 10%, starts sensor investigation, recovers at <=35°C. |
| **Low-Battery Protection (≤30%)** | **Verified** | Dims brightness to 10%, recovers at >=35%, unaffected by charger plug-in alone. |
| **Raw Charging Power Model** | **Verified** | V x I raw power without phone consumption subtraction; verified in unit tests. |
| **Progression-Based ETA** | **Verified** | Live progression calculation; returns null when samples are insufficient. |
| **Voice Announcements & Deduplication** | **Verified** | Priority queue with state-level deduplication and single active TTS channel. |
| **Night Protection (23:00–06:00)** | **Verified** | Suppresses routine notifications while allowing critical thermal safety alerts. |
| **Media Playback Controller** | **Verified** | Only resumes media if paused by Nethra; preserves externally paused states. |
| **Bluetooth Telemetry & History** | **Verified** | Differentiates live connected vs paired history with last-known battery state. |
| **Central Capability Registry** | **Verified** | Truthfully reports `SUPPORTED`, `AVAILABLE`, `PERMISSION_REQUIRED`, `DISABLED`, etc. |
| **Real Android Permission Flows** | **Verified** | Native settings launchers for Usage Access, Notifications, Bluetooth, and Doze. |
| **Geo-Climate & Weather Baseline** | **Verified** | Integrated Open-Meteo REST API, ambient sensor fallback, and climate deviation engine. |
| **Storage & Cache Management** | **Verified** | Automatic cache cleanup above 200MB without touching SQLite databases or settings. |
| **Hardware Charger Control** | **Unavailable / Android Limitation** | Android OS provides no public API to directly throttle external charger wattage. |

---

## 🛠️ Testing & Verification

The project includes an automated test suite executed via Gradle:

```bash
gradle :app:testDebugUnitTest
```

Key test coverage:
- `Part20FinalProtectionIntegrationTest`: Complete 30-case validation of protection limits, recovery thresholds, charging power tiers, event deduplication, and pipeline performance.
- `NetraCentralDataCenterTest`: Concurrency, state deduplication, and atomic flow emission tests.
- `ScreenConsolidationTest`: UI navigation and state binding verification.
- `NightProtectionTest`: Quiet hours announcement suppression and critical safety pass-through.
- `BluetoothIntegrationTest`: Peripheral parsing, connection state transitions, and battery tracking.
- `StorageCacheAndCapabilityTest`: Capability detection and cache size governance.
