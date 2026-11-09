## 2.2.31 - Crash reports only when you send them

- Crash reports stay on your phone now. After a crash you can preview the report and choose Send or Delete - nothing uploads by itself.
- CPU headroom honestly says Unavailable on phones that cannot report it, instead of a wrong warning.
- The app no longer asks for the battery-optimization exemption; it opens Android's own battery settings page instead.
- Thermal alerts say "battery temperature" honestly and never claim the app cools your phone; the 40C marker is an app-chosen reference, not a manufacturer limit.
- Background battery monitoring deduplicated - same tracking, less duplicate work.

## 2.2.29 - Opt-in gauge animations

- New off-by-default switch "Animations" in Settings. While on, the dashboard and charging gauges show small effects INSIDE the circles: a spark while charging, fire when the measured temperature trend is rising and liquid when it is falling (small hysteresis; flat or unknown trends animate nothing), and a heartbeat for battery health life that slows and fades when the health trend falls.
- Every effect follows real on-device readings and the existing freshness/lifecycle gates; nothing is simulated and nothing extra is sampled. Health remains labelled est. when estimated.

## 2.2.28 - Update notices that actually reach you

- New version? The app now checks at every start (and at most every 6 hours), so the in-app notice and notification show up the same day instead of up to a day later.
- Settings no longer shows the long static update explainer; the update card appears only when a newer version is really available.

## 2.2.27 - Quieter continuous alert + percentage announcements

- **Continuous Unplug Alert is voice-only now.** No more ringtone loop at your charge target - just the spoken line repeating until you unplug. The Target Charge Alarm keeps its ringtone option.
- **New: Battery Percentage Announcement (opt-in).** Turn it on in Settings and hear the level at each step: every 5% while discharging (switchable to 10%), and while charging too if you switch that on (every 10% or 5%).

## 2.2.26 - Estimated time (EST) actually shows

- The notification status line showed an estimated time only after the learned charge curve became fully confident, so it kept saying it was learning. It now shows the single-session estimate right away, labelled (est.), and says it is learning only when there is no estimate at all.
- The collapsed notification's second row reads EST ~time to full / EST ~time left when an estimate exists. The charging screen's Estimate row falls back to the background session estimate, labelled (est.). The dashboard gauge marks non-learned ETAs with (est.).
- Estimates come from on-device readings only (session percentage progression and the learned charge curve); nothing is invented when there is no data. No new permission, sampler, dependency, endpoint or stored data.

## 2.2.25 - Battery Health Life in the core ring

- The inner dot ring on the dashboard and charging screen now shows Battery Health Life instead of staying empty: the phone's own reported capacity (full-charge vs design) is used when the phone shares it, otherwise the value is estimated from the latest measured temperature and battery level (heat above 30 C, cold below 0 C and deep discharge below 20% each lower the estimate).
- Estimated values are marked "est." on the gauge; when there are no readings the ring honestly stays Unavailable - nothing is invented.
- Presentation change only, computed on-device from existing readings. No new permission, sampler, dependency, network endpoint or stored data. Not a physical-device battery claim.


## 2.2.24 - GPS power saving (opt-in)

- New opt-in switch "GPS power saving" in Power preferences. While you are moving, location stays on; when the phone is still, location turns off and wakes for a few seconds every 5 minutes only to refresh. Movement is read from the accelerometer; nothing is sent anywhere.
- Android does not let apps switch location by themselves. This feature works only after a one-time ADB grant of WRITE_SECURE_SETTINGS - the same step automation apps like MacroDroid use. Without the grant the switch explains the setup and nothing changes. Turning the feature off restores your previous location setting.
- New manifest declaration WRITE_SECURE_SETTINGS (not grantable at install; used only for this feature).
- This release also ships the two previously merged "Unreleased" changes listed below (inverse temperature dots, community links).

## Unreleased - inverse temperature dots

- The temperature ring starts with 100 dots at 0 C and removes one dot for each rounded degree as the reading rises. For example, 40 C shows 60 dots. This is a display scale, not a safe-temperature target.
- Numeric temperature, warning colours, battery/health rings and finite animation gates are unchanged. Missing temperature stays Unavailable.
- Automated tests and local render fixtures only; not physical-phone tested. No new permission, sampling, telemetry or background work.

## Unreleased - community links

- Settings has five official Netra social profiles, the Battery website and a separate private complaint/feedback form.
- External browser links open only after a tap. No automatic follow, rating or post, new permission or background network request.
- Private feedback is sent through FormSubmit by the web form. No live delivery or physical-phone testing is claimed.

## 2.2.23
Automatic newer-version popup and notification. Metadata is checked at most once daily, subject to Android battery/network limits, not instantly. Update opens this app page in compatible Netra Ecosystem or the Eco website. No manual update check, APK download, installation or unknown-source permission request in this app. Notification denial is respected.

## 2.2.22
Last observed full-charge session after disconnect, with historical first-100% timestamp and connected intervals. Evidence gaps and restarts break continuity. Completed-only private history; one-time speech respects charger/master/mute/media/Night Protection controls. Not exact completion or battery damage. Automated tests/fixtures only, not phone-tested.

## 2.2.21
Three fixed straight notification lines use existing battery/temperature readings; missing or stale data and numeric health stay Unavailable. Android still controls notification visibility. Background charging screen now uses an isolated task and closes without launching the main app; underlying-app/OEM behavior needs phone verification. Charging rings unchanged. No new sampler or permission. Automated tests/fixtures only.

## 2.2.20
Legacy gauge styles and legacy-only colour picker removed. CHARGING/FAST CHARGING label now always uses battery-level colour; the old custom colour pick is gone. Saved old/unknown styles use triple dot rings. Other charging-screen controls retained. Automated tests only, not phone-tested.

## 2.2.19
The ordering test proves helper order only. Paused behavior is supported by code reading and automated tests, not a physical phone. Sticky restarts vary by device/OEM; refused foreground promotion is logged and still stops.
Repeated uncaught crashes pause automatic service starts briefly; prominent in-app warning and explicit retry. Rejected starts are visible. No restart loop or Android restriction bypass. Automated tests only, not tested on a physical phone.

## 2.2.18
Cache auto-clean only above 200 MiB, at most once per six hours; no 50 MB charging cleanup. Bounded diagnostic logs and honest file-payload counters. Manual cleanup stays explicit. Automated tests only, not tested on a physical phone.

## 2.2.17
Weekly/monthly stored-temperature comparisons with explicit day/sample coverage. Sparse data stays unavailable. Observation-only, not measured capacity, lifespan or safety. UTC-day windows; no new sampling. Not physical-phone tested.

## 2.2.16
Three 100-dot rings for battery, temperature and supported health percent. Brief blue join/leave motion follows real sample changes, with idle/background/screen-off/power-saving gates. Unsupported health stays Unavailable. Triple rings default on dashboard and charging screen; labelled legacy styles and their colour controls remain available by explicit choice. Not physical-phone tested.

## 2.2.15
Configurable, silent battery-level recommendations at 50/30/20/10/5%. Android settings shortcuts only, with persistent deduplication and recommendation dispatch after, and isolated from, thermal safety handling. Not physical-phone tested.

## 2.2.14
Separate default-off display/timeout and global-sync consent, persistent saver restore records, visible retry and undo, honest older-version recovery, and manual-only background-process cleanup requests on supported older Android. Notification clearing requires a new opt-in and cannot be undone. Not physical-phone tested.

## 2.2.13
Thermal Guardian adds a critical heat warning at 45 C and above (a warning at most once a minute, no extra actions, no cooling claim). Active control now starts at 39.5 C.
