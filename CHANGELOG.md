# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

- Record available Android battery observations, including charging state, temperature, voltage and current. Missing or stale readings are shown as unavailable or last known instead of invented measurements.
- Classify charging speed from observed battery-side power and track charging sessions. This is not a measurement of the charger's rated output.
- Add a charging-policy state engine with thermal thresholds. The new engine does not yet apply charger limits or system-setting changes; automatic battery protection and restoration are not complete.
- Export recorded observations and charging sessions to a PDF report. Capacity health, battery-failure risk, lifespan gains and unmeasured duration/episode values remain unavailable. The report is not a hardware diagnosis.
- Add GitHub update-checking groundwork: release notes, versionCode-based update detection, APK checksum/package/signing checks and Android user-confirmed installation. Future updates require a higher versionCode and the same signing identity.
- Add crash capture and stability-reporting groundwork. Automatic remote crash-report delivery is not connected to an operational backend endpoint yet; do not rely on automatic GitHub crash reports.
- Add guarded signed-APK release automation with Android build/unit tests, real-PDF instrumentation and CodeQL gates. Production signing credentials are not stored in source control.
