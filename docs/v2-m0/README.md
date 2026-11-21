# BSPN V2.0 M0 evidence ledger

Date: 21 November 2026. Scope: documentation and evidence inventory only. No feature, application, workflow, permission, signing or release change.

## Baseline and outcome

- Repository: prayagi-store-and-services/-Battery-Sentinel-Pro-Netra.
- Source branch: main, commit `24b1959cfd18f34bd23049e6126965959112c025`.
- Latest inspected published release: v2.2.45/code 125 at `9849ed76e4164cac6ff160e5044c99461cfb8384`. Main also includes PR329/330 and the approved ETA-policy PR323 / saver-rollback PR325 merges; no release is implied.
- Current M0 status: PARTIALLY VERIFIED for the static inventory only. Power readiness NOT VERIFIED. M0 is not complete merely because this documentation PR exists or its checks pass.
- Release recommendation: NOT VERIFIED - INSUFFICIENT EVIDENCE.

## Evidence files

`source-index.csv` records paths, SHA256, line counts and pattern candidates for production Kotlin/Java/XML files at the exact baseline. Pattern matches are navigation aids, not a complete call graph or proven defects. XML URLs, finite loops and API type checks can produce harmless matches. The inventory does not execute application code.

`power-budget.csv` has a distinct row for every tier and metric. All measured baselines, finite limits and results remain OPEN - NOT VERIFIED or NOT RUN. No unknown budget means unlimited permission. Proposed local Tier-0 functions need no network; this is a design constraint, not a measured current traffic result.

`device-run-template.csv` defines ten test groups with explicit missing fields. It is an empty evidence form, not test output. Expand groups into individual cases with exact starting battery, runtime mode, screen, thermal, network and OS state; never infer an untested combination passed.

`inventory-summary.json` records mechanically counted source/test files. File counts are not executed tests or passing assertions.

## Static findings and ownership map

| Finding | Existing owner/path under app/src/main/java/com/example | Evidence and remaining question |
|---|---|---|
| Unused power contract | service/PowerWorkDecisionPolicy.kt; CentralPowerWorkContext.kt | Slice1 explicitly has no production consumer. Four-mode enforcement is not established. Reuse NetraCentralDataCenter, not a second state engine. |
| Repeating supervisor | service/BatteryMonitorService.kt:startLifecycleSupervisor | 45-second or 15-minute loop refreshes collectors and metrics. Separate essential freshness/safety duties from optional work before changing it. Timer execution is not an AlarmManager wakeup. |
| Recovery/reports | service/StabilityHealthWorker.kt; StabilityHealthScheduler.kt; StabilitySentinel.kt | Worker catches Throwable and retries without an explicit app cap; hourly request lacks resource constraints. Report queue is bounded to 20, but per-report attempt/expiry policy is not explicit. Actual configured upload endpoint OPEN. |
| Ambient sensor | service/WeatherContextEngine.kt:initAmbientSensor | Anonymous registered listener is not retained for an explicit stop/unregister path. Occurrence on supported physical hardware and energy cost OPEN. |
| Database preservation | data/local/NetraDatabase.kt | Schema 1, exportSchema=false, destructive migration fallback. Future migration risk, not proof of current data loss. |
| Rendering/live screen | MainActivity.kt; service/LiveChargingSessionEngine.kt | 30-second header and 300ms brightness-idle clock; 1-second live screen sampler has explicit pause cancellation. Verify UI call sites; do not mislabel foreground sampling as a proven background leak. |
| Thermal ownership | service/OwnedThermalStatusListener.kt; ThermalGuardianController.kt; NetraCentralDataCenter.kt | Newly owned service callback plus existing thermal controller callback/clock. Preserve unavailable/stale semantics and brightness ownership; reconcile rather than delete protections. |
| Optional hardware | service/GpsPowerController.kt; ThermalCauseInvestigator.kt | GPS-saving accelerometer and thermal-investigation sensor need activation/deactivation cost and mode assignment. Privileged settings require actual grant. |
| Existing bounded state | data/repository/BatteryRepository.kt; data/local/ActivityLogDao.kt | Telemetry writes debounce meaningful changes/time; logs insert transactionally and trim to 2,000. These are useful existing limits, not measured power budgets. |
| Optional network | update/AutomaticUpdateNotice.kt; stats/UsagePing.kt; service/WeatherContextEngine.kt; ai/* | Metadata attempt persisted before network, six-hour throttle and battery-not-low constraint; opt-in counters; weather cache. These do not establish one authoritative power gate for every caller. |

### Startup/call paths inspected

NetraApplication installs crash handling before initialization, restores ownership, initializes Room/settings/central owner/controllers, schedules health and metadata work, and attempts foreground service startup. BatteryMonitorService owns battery broadcasts, lifecycle/screen callback, service thermal callback, notification and cleanup. BootCompletedReceiver attempts service start for boot/update/power broadcasts. ThermalGuardianController separately observes service telemetry and OS thermal state. LiveChargingSessionEngine starts/stops its own foreground-screen sampler. BatteryRepository persists canonical session timestamps and debounced samples. This map is bounded inspection, not an exhaustive transitive call graph.

## Approval and behavior boundary

The proposed four essential functions are local battery/thermal state reception, configured essential safety/charge alerts, durable essential configuration/control ownership, and minimal truthful ongoing status where Android permits it. They are proposals pending explicit plan approval, not a new runtime classification shipped in this PR.

Preserve existing safety thresholds, alarm dismissal, brightness restore ownership, media-skip/audio routing and Bluetooth in-app-only readings. PR323 and PR325 were separately approved and merged before this baseline; unresolved PR324 is outside this documentation scope. No code or workflow is added by M0 documentation.

## Build/dependency/manifest audit gaps

Source declarations: Kotlin 2.4.20, AGP 9.1.1, Java 11 source/target, compile 36.1, target36, min24. WorkManager 2.10.5, Room2.7.0, lifecycle2.8.7, Firebase BOM34.17.0, coroutines1.10.2, OkHttp4.10.0, Retrofit2.12.0. These are not a resolved runtime graph, vulnerability verdict or merged-manifest output.

The inspected Android CI uses Gradle9.3.1/JDK17 and executes assembleDebug and testDebugUnitTest. Existing main Android run: https://github.com/prayagi-store-and-services/-Battery-Sentinel-Pro-Netra/actions/runs/44901117906 . The new PR's exact-head checks must be reported separately; prior main green is not the new PR result.

No local build or physical-device test is claimed. Use existing authorized CI for PR validation without changing workflows. A prior artifact download could not be verified; compiled manifest, resolved dependency graph, transitive SDK initialization and independent artifact test/XML inspection remain OPEN.

## Measurement protocol and exit criteria

Before each approved module, capture a stable exact build and device baseline. Record APK hash, OEM/build/API/RAM, temperature, battery resolution, radios, screen/network, charging, test duration and interfering work. Compare app background work disabled, installed idle and Tier-0-only with repeated matched A/B runs. Use Perfetto/Batterystats, CPU/network/DB counters and supported Power Profiler/Macrobenchmark; ODPM is whole-device energy and cannot be relabeled exact app energy.

Run 8h screen-off and 24h standby where practical; run the three-hour 5% experiment only safely and label approximately 1 percentage point aspirational. Every battery/energy/CPU/wakeup/wakelock/network/memory before/after/change is OPEN - NOT VERIFIED until measured.

M0 exit requires the full feature/call graph and resolved dependencies/merged manifest, approved Tier-0/mode exceptions, baseline runs on selected normal/low-RAM/OEM devices, finite per-tier limits, repeatable tests and rollback fixtures. Device inventory/instrumentation access, exact retry limits and BSPN thermal recovery dwell remain OPEN. Do not mark M0 complete or authorize M1 automatically from a documentation merge.

## Official references checked 21 Nov 2026

- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/google/play/vitals/excessive-wakelock
- https://developer.android.com/studio/profile/power-profiler
- https://developer.android.com/topic/performance/power/battery-historian
- https://developer.android.com/reference/androidx/work/WorkInfo

Foreground specialUse declaration is not Play approval or eligibility to start from every background path. Android16 jobs alongside FGS remain quota-bound. Respect Force Stop and permission loss. No guarantee of uninterrupted monitoring is made. Recheck latest stable Android and current policies at test/release time.
