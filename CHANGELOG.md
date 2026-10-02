# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

- Fix crash: tapping SESSIONS & GRAPH on the Battery screen closed the app. The graph list was placed inside another scrolling list, which Android does not allow. It is now one list, and the Recent Charging Sessions card appears below the graph.
- Fix: the "Allow modifying system settings" switch on Android's Modify system settings screen was greyed out and could not be turned on. The app now declares the permission, so the switch works and you can allow it for the optional "Charging + screen off savings". You still allow it yourself in Android; the app never allows it.

## [1.1.5]

- Permission pop-ups instead of hunting in settings: when something the app uses is not allowed yet (notifications, Bluetooth devices, battery optimization exemption, brightness control if you turned on savings, location), you get ONE short Hinglish pop-up at a time that explains why, with Approve or Skip. Approve opens Android's own screen or dialog - you allow it there, the app never allows anything itself. Allowed permissions are never shown again. If you skip, a later launch asks again ("Aapne pehle skip kiya tha") with allow now, remind me later, or never ask again; never ask again is permanent.
- Festival banner inside the app (same as the website): on a festival day, a country's Independence Day (about 140 countries), or a condolence, a card with a matching accent colour and a live clock appears at the top. It is bundled in the app, so it works offline, and nothing is shown on other days. India first. No death anniversaries. Festival data covers 2026 and 2027; after that no festival banner is shown. Condolence entries are added by hand in the app code (see docs/BANNER.md).
- Monitoring > Hardware: rows that need your action are tappable and open the exact Android settings page, and re-check when you come back. Bluetooth LE shows AVAILABLE only when Bluetooth is on and permission is granted. Exact alarms now show NOT NEEDED because the app uses no exact alarms. Screen-off network optimization now shows UNSUPPORTED, because it needs a system-only permission that no user can grant to a normal app.

## [1.1.4]

- New update notification: once a day (only when there is internet, at a time Android chooses, no exact alarms) the app checks the official GitHub releases. If a newer version exists you get one status-bar notification, "Naya version available hai"; tapping it opens the app, where the existing verified in-app updater offers the download. Nothing is downloaded or installed automatically, and you get only one notification per version. The existing in-app update prompt is unchanged.

## [1.1.3]

- Optional in-app feedback and crash reports (Settings). Nothing is sent without your consent each time, and you first see the exact list: phone model, Android version, app version, and your message or the crash stack trace (code locations only). Nothing else is sent. A privacy policy page is linked from the card and the website.
- New optional "Charging + screen off savings" (Settings, off by default): while charging with the screen off, brightness is lowered and auto-sync is paused; your previous values are restored when the screen turns on or you unplug. Needs the "Modify system settings" permission; without it nothing changes and the card says so.

## [1.1.2]

- Fix wrong battery announcements while discharging: the app announced the lower 5% level as soon as the battery dropped below the previous one (75 to 74 announced "70 percent"). A level is now announced only when the battery actually reaches it (0, 5, 10 ... 100), in both charging and discharging, and never twice for the same level.
- Devices tab now says clearly when Bluetooth is off ("Bluetooth band hai - on karein") or when the Bluetooth permission is missing, instead of showing an empty list. It updates live when Bluetooth is switched on or off.

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
