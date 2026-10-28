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
