# Triple dot rings

The shared component uses real canonical level/temperature values. Each ring has exactly 100 fixed slots. Level uses one percent per dot; temperature is rounded to the nearest degree C, clamped to 0..100 slots. Text retains one decimal place. Values are not a percent-of-safe-temperature scale. Battery health percent is unavailable because the public SDK used by this app does not provide a supported measured percent; Android EXTRA_HEALTH is qualitative and is never converted to 100%.

Only an integer-dot count change starts a finite 480ms transition. Increased count moves blue dots inward; reduced count moves them outward. Dots settle to green/amber/red for the metric. Battery thresholds: <=15 red, <=30 amber; thermal: >=38 amber, >=39.5 red. No idle loop and no new sampling timer. Motion is allowed only while resumed, visible, screen interactive/on, data fresh and Android power saving off. Retained/unknown readings do not animate. A stale field in either level or temperature gates shared motion conservatively. Status, power, ETA and per-field freshness remain on the dashboard.

Charging screen defaults to triple rings, including upgrades. Existing stored gauge values are not deleted; the new style selector requires an explicit legacy selection before using one. The legacy color picker is honestly labelled and applies to legacy gauges, not thermal/battery semantic colors. Gauge brightness applies to either rendering. Clock/details/screen brightness/idle dim/hold/preview/double-tap controls are retained.

Automated rules and Compose tests cover bounded slots, missing health, colors/direction, measured finite transitions, screen-off/stale/offscreen/power-save cancellation, no replay on re-enable, and style persistence. Rendered frames are local fixtures, not device telemetry. Physical-phone/OEM behavior and battery-cost measurement remain unverified.

Public API source: https://developer.android.com/reference/android/os/BatteryManager
