# Battery Sentinel Pro Netra

Android battery monitoring, heat and charge-target alerts, consent-based power-saving tools, and observed charging history.

Current public release: [v2.2.22](https://github.com/prayagi-store-and-services/-Battery-Sentinel-Pro-Netra/releases/tag/v2.2.22), versionCode 102. [Website](https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/) | [Privacy](https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/privacy.html)

## Current protection and power-saving scope (v2.2.22)

- Heat and charge-target warnings, battery/charging readings, voice controls, Bluetooth readings when available, and local history. These do not stop charging at a hardware limit or guarantee cooling, battery life or battery health.
- Optional power-saving controls can lower display brightness/timeout and pause auto-sync only within their separate opt-ins and Android permissions. Their write-ahead restoration ledger tracks saved values and respects later user edits. Restore failures and unknown older-version settings need user attention; exact recovery is not promised for missing originals.
- Active Power Saving is off unless enabled. Its schedule is 23:00-07:00 or 15 minutes screen-off outside that window, with a driving pause. This is not an automatic Android Battery Saver switch. The separate 12-minute screen-off nudge requirement remains unfinished and is not described as shipped.
- Last observed full-charge session: after a validated disconnect, shows the first observed 100% timestamp, observed interval from the starting percentage, and observed time still connected afterward. Unknown data, gaps over 15 minutes, clock discontinuity and process restart break continuity. Starting at 100% cannot invent the earlier duration. Only completed observations are stored privately; existing quiet/master/mute policies apply to speech.
- Updates are metadata-only notices that open the exact BSPN page in compatible Netra Ecosystem or on the Eco website. BSPN does not download or install APKs.
- Physical-phone/OEM acceptance remains pending. No measured heating, drain or lifespan improvement is claimed.

## Description maintenance

Whenever a feature or architecture changes, update the app/store/site/Eco description, README and release notes against the shipped source. Separate planned features from released ones and automated checks from device validation.

## Telemetry and history

- Battery level, charging state, temperature and electrical readings use Android-provided data. Missing values show "Unavailable"; retained values have freshness limits.
- The foreground electrical sampler requests a snapshot once per second only while the app is STARTED. Power is battery-terminal voltage times current, not the external adapter's printed rating. OEM reporting can limit accuracy.
- Charge/discharge ETA uses observed percentage progression. Insufficient evidence produces no estimate.
- Charging history records observed session times, levels, temperature and power. Bluetooth battery percentage appears only when the phone and device report it.
- Reports can export recorded readings and charging sessions as CSV/PDF. Usage Access may provide recent app usage; that is not a direct measurement of each app's battery drain.
- Routine voice announcements respect the existing Night Protection window, master/mute and media controls. Safety warnings have their separate policy. No guaranteed delivery after Android stops an app is claimed.

## Architecture and data boundaries

Jetpack Compose, Kotlin Coroutines/Flow and Room. Existing collectors feed `NetraCentralDataCenter`; the UI and feature policies consume canonical state/events. Completed full-charge summaries use private preferences. Local history does not imply the app has no network features.

Weather, optional usage counting, feedback/crash reporting and update metadata have their own data paths and limits described in [privacy.html](https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/privacy.html) and SECURITY.md. Do not interpret this README as a new permission or diagnostic-upload grant. No token belongs in the APK or repository.

## Build and verification

`gradle :app:assembleDebug :app:testDebugUnitTest`

The 2.2.22 CI suite ran 776 tests with no failures/errors/skips. Automated policy tests and Compose fixture renders are not physical-phone acceptance, OEM drain measurements or proof of cooling. Low resource use and bounded latency are goals, not verified device-wide guarantees. Android/OEM background limits apply.

Use reviewable branches and PRs, not direct main pushes. Merge only after green checks at the exact reviewed head. Before publication, compare the signed APK's package/version, signer, manifest and contents with the reviewed source and prior public baseline. SECURITY.md, What's new, changelog and published descriptions must agree. Keep unfinished roadmap items visible.
