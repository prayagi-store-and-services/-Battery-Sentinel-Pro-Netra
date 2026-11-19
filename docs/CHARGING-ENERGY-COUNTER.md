# Observed energy-counter charging type

Approved Q15 BSPN charging plan. Separate draft from refreshed main `11cf635cda44922d7f1c1daa1020d5898d5cb171`. Pending PR323/324/325 are not modified or folded into this change. No merge/release requested.

## Rate and category

Android ENERGY_COUNTER reports remaining energy in nWh. The existing ChargingSpeedEngine now estimates net stored-energy rate from its change over monotonic elapsed time. This is not charger output, direct charging-input watts or a voltage-times-current speed estimate.

- Reuse existing central battery refresh/broadcast input paths, with optional energy-counter data appended to processRawInput. No new polling, wake lock, service or telemetry clock.
- Bounded 64-point observed ten-second rolling window, plus ten seconds of observed category stability. Partial oldest intervals use decimal rational arithmetic so exact boundaries do not slip below a band through floating-point cancellation.
- Exact categories: positive below 5 W Slow; 5 to below 10 W Normal; 10 to below 20 W Fast; 20 to below 50 W Ultra Fast; 50 to below 100 W Hyper Fast; 100 W or more Extreme Fast.
- Missing, invalid, zero, unchanged/decreasing energy, full/stopped charging, process restart, sparse events or missing elapsed-window coverage produce Unavailable. Missing input never reuses an old category or falls back to Normal.
- Identical duplicate timestamp/value is idempotent. Backward elapsed time starts fresh evidence. No unsupported accuracy or cost claim.
- Existing electrical power/normalization, consumption, ETA, live-session graphs, health and Bluetooth behavior are otherwise preserved. Existing live-session polling is not expanded or used as a second speed owner.
- Legacy SUPER_FAST remains readable as a historical enum; it is no longer emitted. New enum values are appended after existing ones.

## One owner for speech

The existing announceChargingSpeed setting and default ON are reused. Saved OFF is not reset. The existing AnnouncementEngine handles short type-only phrases, existing media/call/night rules and its cooldown/deduplication. No extra TTS instance or queue.

Superseded/unavailable types remove pending type speech. Dispatch rechecks the current category, setting and energy observation age after the asynchronous hop. Old canceled utterance callbacks cannot finish or fail a newer utterance. Type speech never becomes a fallback chime. Start/stop/full remain with existing owners. The legacy electrical-watts classifier entry point returns Unavailable instead of supplying another formula-based classification.

## UI

The existing Battery Power & Electrical Telemetry card keeps its place and layout. It labels the new W value Net stored-energy rate (estimated), separates battery-side sensor power, and explains that the category is not charger output or V x I. The existing card is extracted into a composable for actual screenshot testing, not duplicated.

## Verification

16 local JVM tests pass against this app's actual engine/policy and its extracted real enum, covering exact threshold paths, invalid/sparse data, full/restart, bounded memory, independence from electrical watts and unchanged electrical readings. Existing formula-only tests now explicitly require Unavailable when no energy observations are supplied; their electrical-power assertions remain. Added central integration, expiry, saved-OFF, queue cancellation, six-phrase and actual Compose screenshot tests.

Full Android CI, static checks and real screenshot inspection are pending at draft creation. Physical-device ENERGY_COUNTER support, accuracy, background delivery, TTS routing and measured power/thermal use are NOT RUN. No version bump, signed release, repository setting, secret, permission or billing change.

Official semantics: https://developer.android.com/reference/android/os/BatteryManager
