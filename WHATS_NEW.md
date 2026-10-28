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
