# Recorded temperature comparisons

Observation-only feature: the existing database stores samples, sometimes including retained last-known readings without a freshness flag. It cannot measure health capacity, lifespan, time above a threshold or chemical degradation. Capacity and calibrated failure risk remain unavailable; checkAndNotifyFailureRisk is a no-op and sends no alert, even with more report emissions.

Two adjacent completed-day windows:7+7 and30+30 UTC days. UTC midnight is05:30 in India. Future/current-day records are excluded. Each day needs6 stored samples; weekly needs5/7 eligible days in both windows, monthly21/30. Rules are developer defaults, not statistical confidence. Daily sample means are equally weighted to reduce polling-frequency bias. Usage and missing-day bias remain; a lower number is not a safe-temperature result.

Performance: battery_telemetry currently has no timestamp index. This release avoids a migration and avoids Room invalidation observing the GROUP BY: one suspend query runs on Dispatchers.IO per report screen subscription, no timer and no per-insert scan. Output is at most60 daily rows. A very large existing database may make that single scan slow; no device performance or battery-cost claim. Other report inputs still refresh, but reuse the frozen daily aggregate until the screen subscribes again. The history feed is not the old100-record feed.

No schema changes, deletes, network or extra sensor polling. Tests cover sparse/dense data, unique days, invalid/future inputs, exact weekly/monthly boundaries, UTC rollover, real in-memory Room aggregation, and360dp readable labels. Physical-phone/OEM validation remains unverified.
