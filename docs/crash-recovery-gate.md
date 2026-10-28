# Crash-only service-start guard

Design:3 uncaught crashes within5minutes set a2-minute local pause. One/two crashes do not pause. Crash counting is an early default-handler wrapper before application initialization; existing StabilitySentinel chains to it. Only uncaught Throwables count. Ordinary OS process death, service start refusal and handled exceptions do not. Active cooldown is not extended by further recorded crashes. Clock rollback/implausible future state reset the counter. Persisted synchronous preference commit is best effort; storage failure may prevent counting. No network, alarm, scheduled restart or retry loop.

Gate applies both companion start helpers and both services' onCreate/onStartCommand, including sticky null-intent starts. Paused service onCreate promotes with an honest paused notification on O+ then stops before collectors/normal monitoring; onStartCommand returns START_NOT_STICKY. Teardown tolerates missing lifecycleTracker. App/boot/power/package/visible-entry requests reach the same gate. Boot starter exceptions are isolated. Explicit Retry clears counter and tries both helpers. Refused Android foreground start is shown, without fallback to ordinary startService on Android O+. Pre-O ordinary service start remains. Return from start API means accepted request, not proven running.

UI:prominent error card above active tab explains possible loss of monitoring/thermal alerts and safety limits; explicit Retry. No countdown/timer; expiry is evaluated by next normal start/visible-entry/Retry. Until then old pause notice may remain visible conservatively. Pausing services does not suspend all Application initializers or other unrelated app controllers. Early application initialization can still crash before the UI exists; gate is not a universal safe mode. Retry may crash again; no physical-device recovery guarantee.

Tests: pure1/2/3-crash thresholds, five-minute window/two-minute expiry, no pause extension, rollback; actual persisted preferences and retries;50ordinary checks do not create crashes; Robolectric paused sticky services/create/destroy, FGS refusal noordinaryfallback;360dp warning+retry pixels. No actual uncaught handler kill in test process, no real reboot/Doze/OEM/power test. Parent review + exact-head CI + APK audit still required.

Android sources checked:
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/reference/android/app/Service

Android12+ limits background FGS starts with exceptions; Android14+ adds type/while-in-use constraints. START_STICKY can recreate a killed service; it does not guarantee OEM behavior. No policy compliance certification is claimed.

Sticky-restart foreground-service behavior is device/OEM-variable and not tested on a physical phone. Paused O+ starts post the existing sentinel notification with paused/retry text before stopping, rather than missing the foreground promotion deadline.

Paused foreground promotion catches Throwable, logs refusal and calls stopSelf in finally. The ordering test proves helper order only. Paused behavior is supported by code reading and automated lifecycle tests, not a physical phone.
