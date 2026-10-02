# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

- Fix wrong battery announcements while discharging: the app announced the lower 5% level as soon as the battery dropped below the previous one (75 to 74 announced "70 percent"). A level is now announced only when the battery actually reaches it (0, 5, 10 ... 100), in both charging and discharging, and never twice for the same level.
- Devices tab now says clearly when Bluetooth is off ("Bluetooth band hai - on karein") or when the Bluetooth permission is missing, instead of showing an empty list. It updates live when Bluetooth is switched on or off.
- Optional in-app feedback and crash reports (Settings). Nothing is sent without your consent each time, and you first see the exact list: phone model, Android version, app version, and your message or the crash stack trace (code locations only). Nothing else is sent. A privacy policy page is linked from the card and the website.

## [1.1.1]

- Fix the Devices tab not listing a connected Bluetooth device on some phones (reported on a Realme 9 Pro 5G). Connected devices are now found through more routes and one failing check no longer hides every device.
- A Bluetooth battery percentage is shown only when the phone and the device report it. Android has no public battery API for classic Bluetooth devices, so on some phones the card shows "Battery: Unavailable" instead of a made-up value.

## [1.1.0]

- Live Power card now shows live voltage (mV), current (mA), calculated battery-side power (W), battery temperature, battery percentage and a session duration timer, in both charging and discharging. Full and Not charging are shown separately. Time to full (charging) and time until empty (discharging) are estimated from the last 10 minutes of real percentage progress and show Calculating... or Unavailable when data is missing or inconsistent. Values the phone does not report show Unavailable, never a made-up number. Not yet verified on a physical device.
- Pin patched versions of vulnerable build-tool dependencies (Netty, Bouncy Castle, JDOM2, HttpClient, commons-lang3, Guava, jose4j) in the Gradle build. This changes the build, not app features. Some Dependabot alerts may stay open until they are re-scanned.
- Refresh the Security Policy (SECURITY.md) to describe the current permissions, data handling, release signing and planned solar rules, and add docs/AI_RULES.md with the project's working rules.

## [1.0.0]

- Record available Android battery observations, including charging state, temperature, voltage and current. Missing or stale readings are shown as unavailable or last known instead of invented measurements.
- Classify charging speed from observed battery-side power and track charging sessions. This is not a measurement of the charger's rated output.
- Add a charging-policy state engine with thermal thresholds. The new engine does not yet apply charger limits or system-setting changes; automatic battery protection and restoration are not complete.
- Export recorded observations and charging sessions to a PDF report. Capacity health, battery-failure risk, lifespan gains and unmeasured duration/episode values remain unavailable. The report is not a hardware diagnosis.
- Add GitHub update-checking groundwork: release notes, versionCode-based update detection, APK checksum/package/signing checks and Android user-confirmed installation. Future updates require a higher versionCode and the same signing identity.
- Add crash capture and stability-reporting groundwork. Automatic remote crash-report delivery is not connected to an operational backend endpoint yet; do not rely on automatic GitHub crash reports.
- Add guarded signed-APK release automation with Android build/unit tests, real-PDF instrumentation and CodeQL gates. Production signing credentials are not stored in source control.
