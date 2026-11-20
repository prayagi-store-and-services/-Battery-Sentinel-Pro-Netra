# Battery monitor thermal-listener lifecycle

Q32 option3,20November2026. PR only. No merge/release approval is implied.

BatteryMonitorService owns exactly one platform thermal listener through a small lifecycle owner. Registration is API guarded and failures publish unavailable with an explicit reason, never NORMAL. The initial central status is null until a real callback. A real platform0 still means observed NONE. Unknown out-of-range readings are unavailable.

Stop rejects queued callbacks before removing the exact registered instance. The generation guard rejects old callbacks after restart. Removal failure retains the exact instance and blocks a duplicate registration. A later explicit stop may retry cleanup. Registration refusal attempts cleanup once in case a vendor registered before throwing; if cleanup fails, ownership is retained. There is no timer or automatic retry.

The existing ThermalGuardianController has its own policy consumer and owned platform listener. It is unchanged. This repair does not claim to collapse those two separate existing consumers into a new shared engine. Battery-temperature safety, thresholds, policy clocks, cooling, modes, TTS and notifications are unchanged.

Fifteen focused unit cases cover initial unknown, actual normal/severe, synchronous callback, unsupported API, invalid value, duplicate start, registration refusal and cleanup, destroy before start, exact removal, late callbacks, restart, removal failure and partial-registration cleanup failure. CI results must be reported after running. Physical phone, frequency, battery-drain and runtime lifecycle testing are NOT RUN. No visible layout changed and no visual/device readiness is claimed.

Existing PR323 also touches BatteryMonitorService, but its diff changes charge-target ETA fields/calculation, not this listener lifecycle. It remains separate and unmerged.
