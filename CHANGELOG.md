# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

- Last observed full-charge session appears on the dashboard after disconnect: first observed 100% time, observed connected interval from the starting percentage, and connected time afterward. Historical timestamps are labelled. No exact physical completion, battery-damage, capacity or lifespan claim.
- Existing validated raw battery events only; no new sampler. Unknown input, gaps over 15 minutes, clock discontinuity and app restart break the active observation window. Starting at 100% cannot invent a completion duration.
- Completed summaries are saved privately only at disconnect. Speech is claimed once before enqueue, uses existing charger/master/mute/media controls and respects Night Protection without catch-up speech. Durations below one minute are described honestly.
- Automated tests and actual Compose fixture renders only, not physical-phone tested. No new permission, dependency, network endpoint or database schema. Not a security patch.

## [2.2.22]

- Last observed full-charge session appears on the dashboard after disconnect: first observed 100% time, observed connected interval from the starting percentage, and connected time afterward. Historical timestamps are labelled. No exact physical completion, battery-damage, capacity or lifespan claim.
- Existing validated raw battery events only; no new sampler. Unknown input, gaps over 15 minutes, clock discontinuity and app restart break the active observation window. Starting at 100% cannot invent a completion duration.
- Completed summaries are saved privately only at disconnect. Speech is claimed once before enqueue, uses existing charger/master/mute/media controls and respects Night Protection without catch-up speech. Durations below one minute are described honestly.
- Automated tests and actual Compose fixture renders only, not physical-phone tested. No new permission, dependency, network endpoint or database schema. Not a security patch.

## [2.2.21]

- The ongoing notification has three fixed horizontal tracks: top battery percentage, middle temperature (1 C per unit on a 0-100 display scale), bottom supported numeric health. Colours match the charging-screen metric colours. Tracks update from existing readings without an animation or extra sampler. Missing or stale readings have no coloured fill; health stays Unavailable because no numeric health source is exposed here.
- Compact shows the three metric values; full status/ETA remains in the expanded view and accessibility description because the compact content is limited to 48 dp.
- Compact and expanded notification layouts use Android's decorated template and native value text. The system controls the header, spacing, permission and visibility; Android 14 can allow the user to dismiss an ongoing notification. This is not an always-visible guarantee.
- The background charging screen has an isolated task and closes only that task, rather than reusing the main app task. Double-tap does not explicitly launch Netra or another app. Android resumes the underlying task; physical cross-app, lock-screen and OEM behavior is not yet verified. In-app preview still returns to its settings. Charging-screen rings are unchanged.
- Automated tests and actual RemoteViews fixture renders only, not tested on a physical phone. No new permission, dependency, network endpoint, sampler or database schema. Not a security patch.

## [2.2.20]

- Removes the six legacy charging-screen gauge styles and their legacy-only colour picker. Triple dot rings are now the only gauge, including for saved removed or unknown style ids. Old ids are not reindexed into new choices.
- The CHARGING/FAST CHARGING label now always uses battery-level colour; the old custom colour pick is gone.
- Clock, detail layout, selected details, gauge brightness, active/idle screen brightness and close/hold behavior remain. Ring metric colours are unchanged; unsupported health stays Unavailable. Inert old colour preferences are retained, not repurposed.
- Automated tests and fixture renders only, not tested on a physical phone. No new permissions, dependencies, network endpoint or schema. Not a security patch.

## [2.2.19]

- Adds a local crash-only service-start breaker: three uncaught crashes within five minutes pause automatic and sticky service starts for two minutes. One or two crashes do not pause starts; ordinary Android process death and rejected starts are not counted. There is no restart timer or alarm. A later normal start can resume after expiry; Android can still refuse it.
- A prominent in-app warning says background monitoring and thermal alerts may be unavailable while paused, with an explicit Retry monitoring now button. Rejected service starts also show a retry warning. An accepted start request is not proof of an operational service.
- Installs the crash counter before application initializers, isolates boot/package/power service starters, guards monitor teardown after partial startup, and removes the immediate ordinary-service retry after a refused foreground-service start. Visible app entry and explicit Retry remain start paths; no Android restriction is bypassed.
- Engineering recovery guard, not complete crash recovery or a security patch. It does not bypass a crash in application initialization, guarantee reboot/Doze behavior, or measure battery cost. Persistence failure can prevent crash counting. Automated tests only, not tested on a physical phone.
- Paused O+ service paths attempt the sentinel foreground notification before stopping; promotion failures are logged and still stop. The ordering test proves helper order only. Paused behavior is supported by code reading and automated tests, not a physical-phone test. Sticky-restart behavior is device/OEM-variable.

## [2.2.18]

- Automatic maintenance no longer cleans at 50 MB during charging. It scans at most once per six hours, across restart, and cleans only when disposable app cache files are strictly above 200 MiB (200 x 1024 x 1024 bytes). Manual Clean Temporary Cache remains an explicit action below the threshold.
- Only regular disposable files inside this app's internal/external cache are counted and removed. Code cache, child links, database/XML files and saved data are excluded. Removed bytes are file payload lengths only; no invented folder allocation. Incomplete scans show Unavailable and skip deletion; depth/entry traversal is bounded. Old unverified freed-byte counters are hidden until the next cleanup.
- Diagnostic activity logs retain the latest 2,000 entries in insertion order with bounded text. Thermal logs keep the latest 30 sessions and 200 events per session, with at most 500 characters per event. Older diagnostic entries are rotated; battery and charging history are not deleted.
- Local maintenance fix, not a security patch or a claim of full reliability acceptance. No physical-phone, power-cost, crash-loop, reboot or Doze acceptance test for this release. No new permission, dependency, network endpoint or database schema.

## [2.2.17]

- Adds weekly/monthly comparisons of stored daily-mean battery temperature, separate from capacity health. Each completed UTC day needs at least six stored samples; weekly needs five of seven days in each window, monthly21 of30. These are developer coverage rules, not statistical confidence percentages.
- Both current and prior window coverage are shown. Sparse history shows unavailable, never a lifespan or measured-capacity prediction. Existing stored rows may include last-known temperatures, so comparisons are observation-only, not heat exposure, safety or degradation diagnosis.
- The history table has no timestamp index. Aggregation runs once on IO per history-screen subscription, not on each telemetry insert. UTC midnight is05:30IST; comparisons refresh on screen load. Existing five charging tiers are unchanged.
- No new sampling, permission, dependency, network endpoint or database migration. Not tested on a physical phone; no battery-cost claim.

## [2.2.16]

- Battery display now defaults to three concentric rings with 100 fixed dots each: outer battery percent, middle temperature (one degree C per dot), and inner battery health percent. Unsupported health percent stays Unavailable, never a converted Android health label.
- Real integer sample changes briefly show blue dots moving inward for rises and outward for falls, then settle to the metric colour. No idle charging loop; motion stops offscreen, in the background, with the screen off, on stale readings or in Android power saving.
- Dashboard replaces its old arc gauge with rings, with no legacy opt-out there. Charging screen shares the rings. Charging screen keeps clock, details, brightness, close/hold behavior and working legacy gauge/color choices. Triple rings become the default; the charging-screen style picker alone offers labelled legacy styles by explicit choice. Existing legacy indices are initially retained, but a later settings save can overwrite that shadowed value. Triple-ring colours follow battery and thermal levels; the legacy colour picker does not override them.
- Temperature uses an unconfirmed implementation assumption of one degree C per dot. Developer colour thresholds are battery red <=15%, amber <=30%, and temperature amber >=38 C, red >=39.5 C. These are display thresholds, not medical or OEM safety guarantees.
- No new permissions, dependency, network endpoint or health estimate. Not tested on a physical phone. Automated rendering and motion/stop-gate tests use local fixtures, not live-device measurements.

## [2.2.15]

- Adds configurable, silent power-saving suggestions at 50%, 30%, 20%, 10% and 5% while on battery. Buttons open Android Battery Saver and display settings; no setting changes automatically.
- Suggestions retain deduplication across restart. Skipped levels show only the most urgent suggestion; a fresh charger connection or a five-point rise rearms eligible levels. Suggestions run after, and are isolated from, thermal safety handling.
- Not tested on a physical phone. Automated rules, dispatch, recovery and 360dp layout checks cover this release; OEM behavior and power impact still need device validation.

## [2.2.14]

- Automatic brightness/timeout changes and global auto-sync pause now require separate opt-ins, off by default. Modify system settings permission alone does not count as consent.
- Savers share persistent ownership and restore records. Undo and retry show actual readings and pending failures; later values are compared before restore. Ambiguous older records ask you to check Android settings instead of inventing missing originals.
- Other apps' background processes are never cleared automatically. An explicit request is available on supported Android 13 and older; Android 14+ cannot end other apps' processes. Notification clearing has a new default-off consent switch and cannot be undone.
- Fixed a state-publication race that could overwrite concurrent media, Bluetooth or control updates during battery telemetry updates. This is a robustness fix, not a security patch.
- No physical-phone test for this release. Automated recovery and concurrency tests, CI, and a 360dp local render passed; OEM behavior still needs device validation.

## [2.2.13]

- Improved: Thermal Guardian now has a critical heat level at 45 C and above. At that level the app only warns you (a notification and a spoken message, at most once a minute) and tells you to stop using and stop charging the phone in a safe, shaded place and to follow the phone maker's instructions. It does not take extra actions and cannot cool the phone. Active control now starts at 39.5 C instead of 40 C (high warning and cutting the app's own work). Missing sensor readings still show Unavailable.
- Not tested on a physical phone for this release; covered by unit tests and automated builds only.

## [2.2.12]

- Updates now show a metadata-only newer-version notice and open the exact BSPN update page in compatible Netra Ecosystem or the Eco website. BSPN no longer downloads or installs APKs.
- Retires legacy update jobs, installer notices and cached APKs. Version checks share a six-hour cache and failure backoff; Check again can refresh manually.

## [2.2.11]

- Fixed: announcement enqueue and queue processing now share the same lock, preventing background producers from changing the queue while it is being processed.
- Fixed: missing battery temperature and missing or non-positive voltage now show Unavailable instead of default sensor values. Real zero and subzero temperatures remain valid; available battery percentage is unchanged.

## [2.2.10]

- Security: the cache warning opens a fixed internal activity using an explicit, immutable notification intent, before opening Android storage settings. It no longer gives the notification an unspecified intent destination.

- Fixed: in the night (silent) window the full-charge alert no longer speaks, repeats, plays the ringtone loop or vibrates. It shows one silent notification instead. Outside the window nothing changes.
- Improved: the Devices and Logs screens now use the same card spacing as every other screen.

## [2.2.7]

- Fixed: crash reports sent from this app were cut off before the real cause when the crash was wrapped in other errors. They now keep the root cause and a larger part of the trace. Reports still contain only class names and code locations, never messages.

## [2.2.6]

- Fixed: "Session Started" and the charger connect and disconnect times on the Battery screen also restarted after an app update. They are now saved on the phone and keep their real time across updates and restarts. If the charger was plugged or unplugged while the app was not running, the new time starts from the first reading the app sees. (Uninstalling the app or clearing its data resets them.)

## [2.2.5]

- Fixed: after an app update the "On battery for" time restarted from zero. The time the charger was last removed is now saved on the phone and survives updates and restarts, so the count keeps running from the real unplug moment. (It cannot survive uninstalling the app or clearing its data; then it starts again from the next unplug the app sees.)

## [2.2.4]

- Added: the Netra Ecosystem page link for this app (prayagi-store-and-services.github.io/netra-eco/app/bspn) can now open the installed app directly when Android verifies the link; otherwise it stays in the browser. Improvements and bug fixes.

## [2.2.3]

- Fixed: the app list on the App Drain tab added whole calendar-day totals, so it could show up to two days of on-screen time under a last-24-hours label (this app showed 247 min). It now counts exact on-screen time inside the last 24 hours from usage events, says plainly that it is screen time and not battery use, and shows this app's own measured CPU time since it started.

- Fixed: junk and cache over 200 MB went unnoticed because the app only measured its own cache folders. It now reads the total cache of all apps (needs Usage Access; without it the screen says Unavailable and why). When it reaches 200 MB you get one warning with a button that opens Android storage settings, because Android does not let any app clear other apps' cache. It warns again after 12 hours or 500 MB more growth.

- Changed: Thermal Guardian now works on its own when the battery reaches its temperature thresholds. It no longer speaks or posts a notification while it dims the screen, lifts its controls or lists heavy apps; everything is still recorded in its log. Only the Test button speaks.

- Fixed: Thermal Guardian could lift its screen dimming and say the phone had cooled while the phone's own thermal status still reported severe or critical, if the battery reading itself looked cool. It now needs both a cool battery held for a minute and the phone's own severe or critical status cleared before it restores anything. The phone's own thermal safety still always wins.

## [2.1.25]

- New: the charge and time-remaining estimates now learn this phone's own charging curve. The app remembers how many seconds each battery percent really takes on this phone, separately for charging and discharging and for cool, warm and hot batteries, so the 90 to 100 percent slow-down and heat throttling are measured, not guessed. Until enough real data exists the estimate says the app is still learning this phone's pattern instead of showing a number.
- Fixed: the charge target timer used to stretch the linear full-charge estimate, which read far too low when the phone slows near full; it now uses the same learned curve to the chosen target.
- Changed: the two ongoing background notifications are now one single-line notification: percent, temperature and the honest time estimate or learning state. Alert notifications are unchanged.
- Changed: every spoken announcement now takes transient audio focus, so music and videos pause while the phone speaks and resume when it finishes.

## [2.1.24]

- New: Settings now shows an Auto-Launch card on realme, Oppo, OnePlus, Xiaomi, vivo and Huawei phones. These brands stop 24/7 background monitoring with their own auto-launch switch even after Android's battery exemption is allowed; the card opens the phone's own auto-launch screen when the phone has one, and says Unavailable with manual steps when it does not.

- Fixed: every feedback and crash report now names the app ("Battery Sentinel Pro Netra") in its own field, so a mail can never arrive with an empty app field. The pre-send screen shows the same line.

- New: "Battery Drop Suspects" on the per-app battery usage screen. Pick a window (1, 3 or 12 hours) and see which apps were actually active - on screen or running a background service - while the battery fell, so a fast drain can be matched to whoever was busy during it. Android does not give any app per-app battery numbers (mAh or percent), so these are activity times, never battery share; the phone's own battery usage screen opens with one button. If a fast-drop window shows no active app at all, the list says so plainly, which points at the battery or phone instead of an app.
- New: "Battery Hardware Facts" on the same screen: the battery's own health self-report, real temperature, voltage and level, plus an honest note that Android exposes no capacity (mAh) or cycle count, so a worn replacement battery cannot be told apart from an app by software.

## [2.1.21]

- Fixed: the continuous unplug alert now starts only when the battery reaches the charge target you picked (80, 85, 90, 95 or 100 percent) and repeats at the steps at or above it. Before, it could already start at 80 percent while charging even when a higher target was set. Levels you did not select never ring.

## [2.1.20]

- New: continuous unplug alert. When the battery reaches 80, 85, 90, 95 or 100 percent while a charger is connected, the phone's ringtone plays and the spoken level alert repeats about every 10 seconds until the charger is unplugged. Unplugging is the only way to stop it; there is no dismiss or snooze. It follows the master announcements switch and has its own switch in Settings. Nothing new is stored or sent.

## [2.1.18]

- New: optional "Lock the screen when charging stops" (Settings, off by default). If you switch it on and allow Android's Device Admin permission, the phone locks and the screen goes off at once when charging stops while the charging screen is showing. The permission only allows locking the screen, and you can remove it in the app or in Android Settings.

## [2.1.17]

- Fixed: the charge target alert (80, 85, 90, 95%) now also fires when the phone says it is plugged in but not charging at that level, which some phones do near a limit. Settings now shows whether the alert is armed or when it last fired, and a Test alert button checks that the notification and voice line can reach you.

## [2.1.16]

- New: the Saver can now start early. If the battery temperature rises fast (at least 1.2 C within about 3 minutes, from 33 C up, not charging), it starts before the temperature limit is reached. The rule is shown in the Saver settings, where you can switch it off or change the rise and the starting temperature. It only reads the battery temperature and cannot cool the phone itself.
- Changed: the Saver no longer closes apps you used in the last 15 minutes (it needs Usage Access to know; without it, it closes nothing), and an early start keeps your screen timeout as it is, so it does not disturb an app you are working in.

## [2.1.15]

- New: when your phone is warm (40 C or more) and Usage Access is on, the Status screen lists the apps you had open in the last 30 minutes. This is an estimate only: Android does not report heat per app, and heat from background apps is not shown.

## [2.1.14]

- Improved: when an update is available and your phone is on Wi-Fi or mobile data (under about 100 MB, not roaming, Data Saver off, battery 20% or more or charging), the update is now downloaded in the background and checked. You get a notice to tap to install, and Android asks you to confirm the install.

## [2.1.13]

- Fixed: the app usage screen no longer shows two boxes that always said Unavailable (per-app energy and high-drain apps). Android does not give apps that data. The real count of listed apps stays, and only when Usage Access is on.

## [2.1.12]

- Fixed: the battery check no longer says it is powered by Gemini AI. The tips are worked out on your phone, nothing is sent anywhere, and a few over-strong claims in the tips were toned down. The battery history insights are also worked out on your phone only.

## [2.1.11]

- Fixed: the weather lookup now sends only an approximate location (about 1 km), never your exact position.

## [2.1.10]

- Fixed: the anonymous usage count is now off by default. Nothing is sent until you turn it on in Settings.

## [2.1.9]

- Added: a Battery Health card. It shows charge cycles and battery capacity (design and full-charge) when your phone shares them, and a clearly labelled estimate otherwise. It stays hidden if your phone shares nothing.
- Added: the always-on charging control now has ON, LOCK and OFF modes.

## [2.1.8]
- Fixed: the charging screen now turns off when charging stops. It keeps your phone awake only while the phone is really charging. When you unplug, or charging stops for any reason (including full), it lets go and closes, and your phone's own screen timeout works again.
- Added: a Preview button next to the charging screen setting. It shows how the charging screen looks without turning anything on and without keeping the screen awake.

## [2.1.4]

- Added: the app is not available in some regions. It checks only your phone's own SIM/network country, on the phone, and sends nothing anywhere. If it cannot tell, it works as normal.

## [2.1.3]

- Fixed: the 24-hour battery trend chart could crash on some phones. It now draws its time labels safely.

## [2.0.9]

- Added: Battery Booster in Settings. It does not close other apps, because since Android 14 Android does not let an app do that. It opens your phone's own battery usage and restriction screens (only when the phone has them) and has a Start/Read drain check that shows percent lost per hour. It cannot tell which app caused the drain.
- Fixed: the Saver's "Close background apps" option did nothing on Android 14 and newer but still reported free RAM. It is now switched off there and says so; on Android 13 and older it works as before.

## [2.0.8]


- Changed: if the phone is still charging at or above your charge target, the target alert now repeats about every 2 minutes until you unplug the charger or tap Dismiss Alarm (before, it rang once). The alert now says plainly that the app can alert you but cannot stop charging, because Android does not let apps do that.
- Changed: when you come back to the app it now also deletes update installer files older than one hour from its own private folder, so nothing from an update stays in storage. Nothing outside the app's own folder is touched.

## [2.0.6]

- Fixed: tapping Update made the update window disappear, so nothing looked like it was downloading. The window now stays open and shows the real progress: percent downloaded (counting up), megabytes done of total, a progress bar and the time left (it never goes up). The same progress shows in the App updates card.

## [2.0.5]

- Fixed: the charging screen was too bright. It now opens at 30% brightness by default, and a new slider "Screen brightness while it is open" lets you set it from 5% to 100% (100% means the phone's own setting). The idle dim after 15 seconds can never be brighter than that.

- What's new: a new switch "Charging screen on" in the Charging screen card. Turn it off and the charging screen never opens by itself (app open or closed). "Open charging screen now" still works.

## [2.0.4]

- Fixed: the screen could stay stuck at 10% brightness. The temperature and low battery protection dim now only applies after 15 seconds without a touch, any touch brings normal brightness back at once, and it is released when you plug in the charger or the phone cools down. The charging screen also restores brightness the moment the charger is unplugged.

- What's new: update alert. When a new version is out, a notification appears (checked every 6 hours). Tapping it downloads the new build and starts the install straight away. Android may still ask you to allow installs from this app once.

## [2.0.2]

- Fixed: the Estimate time on the charging screen and in Live Power went up instead of down. It now counts down to the charge target you picked (for example "Estimated Time To 80%") and only recalculates when the battery percent changes, so it never climbs between readings. It shows 00:00:00 once the target is reached.

## [2.0.1]

- What's new: shorter announcements. Plugging in says "Power connected, 42 percent." and unplugging says "Power disconnected, 42 percent." (the percent is left out at 100 and at your own target). The extra "Charging started", "Charging stopped" and "Discharging started" lines are gone. Bluetooth says "BT Buds, 60 percent." on connect and on disconnect, or only "BT Buds." when the device gives no battery. Phone battery says only "80 percent". Heat and power saving messages are shorter.
- Fixed: the charge target you pick (80, 85, 90 or 95) was only a notification and was never spoken. It now speaks "85 percent. Target reached." the moment that level is reached, and also if you change the target while charging.

## [2.0.0]

- What's new: Battery Sentinel 2.0. The charging screen is now yours to design. In the Charging screen card you can pick the clock style (Off, Light, Regular, Bold, Outline, Mono), the battery gauge style (Ring, Dotted ring, Arc, Bars, Battery bar, Number only), the battery details style (Plain, Boxed, One line), which details show (Temp, Voltage, Wattage, Current, Estimate, Charging time), the gauge colour (Auto follows the battery level, or one of 8 colours), the gauge brightness, and how dim the screen gets when idle (5 to 50%, default 10%). Every choice is saved on this phone only. Not tested on a phone by the developer. Dot-matrix and animated clock or gauge styles are not included yet.

## [1.2.15]

- What's new: when the update check or download fails, the app now shows one plain sentence (for example "No internet, or the server did not answer") instead of raw system text. The app's own messages are unchanged. No new permission.
- What's new: charging screen update. The battery percentage now shows the whole number Android reports (for example 73%) instead of 73.00%, because Android only gives whole numbers. The ring and the percentage change colour with the level, using the same colours as the rest of the app (75% and up green, 50% and up light green, 20% and up amber, below 20% red). The screen dims to 10% brightness after 15 seconds without a touch; a touch or unplugging the charger brings normal brightness back. The charging screen switches are now on by default (showing over other apps still needs you to allow Display over other apps in Android). Not tested on a phone by the developer.

## [1.2.14]

- What's new: festival banner cleanup. Five entries that are not Indian festivals (Lunar New Year, Passover, Friendship Day and the two Hanukkah days, in 2026 and 2027) are removed, so the banner only shows days from the Indian list. No other change.

## [1.2.13]

- What's new: new charging screen. With the charger connected and the screen turned off or the phone locked, a black screen shows a big clock, the date, a battery ring with the percentage, the charging label, temperature, charging time, estimate and power. It shows over the lock screen without unlocking the phone. It opens at once when you plug in, comes back every time the screen turns off while charging (it stays dark and shows when you next wake the phone), and closes on a double tap (the app you had open comes back) or when you unplug. It uses the existing "Also open it when the app is closed" switch (off by default) and Display over other apps, which you already allow there. "Fast charging" appears only when the measured power is 15 W or more; any value Android does not give shows Unavailable.

## [1.2.12]

- What's new: safer Battery Saver display restore. If Android's "Modify system settings" permission is missing when the phone has cooled down, the saved brightness and screen timeout are no longer thrown away: they are kept, the Saver card says "Unavailable" with the reason, and they are put back as soon as the permission is allowed. If you change brightness or screen timeout yourself while the saver is on, your choice is kept and only settings still at the saver's value are put back. A saved restore point is no longer overwritten by a second run. No new permission.

## [1.2.11]

- What's new: truth fix in Settings. A line called the monitor an "Ultra-Low Power 24/7 Event-Driven Architecture", which the app cannot prove: it also re-checks on a timer. It now says what the code does: it reacts to Android battery events and re-checks every 45 seconds (every 15 minutes in the ideal charge state) while the monitor runs. No new permission.

## [1.2.10]

- What's new: truth fix on the Monitoring screen. The cache row said "User Data Protection: Guaranteed", which the app cannot prove. It now reads "Cleanup scope: Cache files only", which matches the cleanup code: it deletes only cache folders. No new permission.

## [1.2.9]

- What's new: a Permissions list in Settings. It shows every permission the app uses (notifications, location, nearby devices, phone state, display over other apps, modify system settings, secure settings, battery optimisation, install apps and the normal always-allowed ones) with the plain reason and the live status read from Android. Tap a row to open the matching Android page. Secure settings can only be granted from a computer, so that row has no button and says so. No new permission is added.

## [1.2.8]

- What's new: one home screen widget, rebuilt. All 13 old widgets were empty and did nothing, so they are removed (if you added one, remove it from the home screen and add the new one). The new "Netra Sentinel" widget shows, in one card: battery percent, charging or discharging, power source and charging speed, temperature, voltage, current, power in watts, battery health, time to full or time left, and the time of the last reading. Anything the phone does not report shows Unavailable. It redraws only when the app records a new reading, with no timer or extra background work, and says "Stale" if the last reading is older than 15 minutes. Its colour and background are set in Settings, Widget style. The "Widgets" button on Home is removed.

## [1.2.7]

- What's new: the Charging screen can now open by itself when you plug in the charger, even when the app is closed or in the background. It is a second switch under the Charging screen card, off by default. Android only allows this after you grant "Display over other apps" to this app, so the switch opens that Android screen and stays off until you allow it. It uses the charger-connected event the app already listens for, with no extra polling. It cannot open over a locked screen. Tap the screen to close it.

## [1.2.6]

- What's new: optional Charging screen (Battery tab, under Live Power). A plain black screen with the clock and the live battery values from this phone: percentage, charging state, power, voltage, current, temperature and the time estimate, each shown only when Android reports it. It keeps the screen on while open; tap to close. A switch, off by default, opens it when you plug in the charger while the app is open. Opening it while the app is in the background is not included yet because it needs a permission; that is the next part.
- What's new: Android app backup is turned off. The old setting copied the app's saved data to Google backup with sample rules that limited nothing. No new permission or library.

## [1.2.5]

- What's new: Power Preferences now really work. Brightness preference lowers the screen to a saving level and puts your old brightness back when you turn it off; the first time it opens Android's "Modify system settings" screen so you can allow it. Saver profile does brightness and, only if allowed, the phone's Battery Saver. Power-saving preference switches the phone's own Battery Saver, but Android only allows that after a one-time command from a computer, so without it the switch stays locked and shows the command.
- What's new: new "Send crash report" button in Feedback. You see exactly what is sent (phone model, Android version, app version, crash code locations) before you tap Send. If no crash is saved it says Unavailable.
- What's new: fixed automatic crash reports. A report counted as sent on any reply and was deleted even when the email service had not accepted it. Now it counts as sent only when the service confirms, otherwise it is kept.

## [1.2.4]

- What's new: help where the "Restricted setting" block appears. When Android greys out Notification access for this app (it does this for apps installed from a file), the Saver card now shows a step by step guide: press and hold the app icon, tap App info, tap the three dots, tap Allow restricted settings, then switch notification access on. It also explains that the menu item disappears after you tap it, which means it worked. The guide is a drawn illustration, not a real screenshot, and the app says so. Android gives no way for an app to unlock this itself.

## [1.2.3]

- What's new: the header is now the Netra standard, 56 dp tall, showing only the app name, the installed version and the date and time. The festival banner, the Widgets button and the Refresh button moved to the top of the scrolling Home screen, so everything except the header and the bottom bar scrolls. The live battery and temperature capsule left the header; the same values are on the Home screen.
- What's new: two more dummy controls removed. The lightning button in the header and the red "Ultra Battery Saver" banner only stored a flag and changed nothing on the phone.

## [1.2.2]

- What's new: Saver restore fixed. Before, when the phone got hot and the Saver lowered brightness and screen timeout, it only put them back once the battery level was ALSO above your limit, so with a lower battery it never restored even after the temperature dropped. Now it remembers why it started: a heat start restores as soon as the temperature is 2 degrees C below your limit, a low-battery start restores when the level recovers or charging begins. It also kept being skipped while the app was in Ideal State; it now always sees the battery reading. Closed apps and cleared notifications cannot be brought back by Android, only brightness and screen timeout are restored.
- What's new: dummy controls removed from Settings, Status and the quick actions sheet. "Ultra Battery Saver", "Power-saving" and "Brightness" switches only stored a flag and changed nothing on the phone; they are now locked off and marked Unavailable. The "Export Summary" button only showed a message and exported nothing; it is removed. Every other control was checked and does real work.
- What's new: Saver, "Clear notifications": on Android 13 and newer, a phone can block "Allow notification access" for an app installed from a file, so the switch is grey and cannot be turned on. The Saver now shows exact steps for this (App info, three dots, "Allow restricted settings", then notification access) and an "Open app info" button. Honest limit: Android gives apps no way to unlock this themselves or to tell for sure that the block is on, so the steps are shown whenever notification access is off on Android 13+ and the app was not installed from Google Play or by this app's own updater. Updates installed with "Check for update" inside the app use Android's session installer, which per published Android descriptions is not blocked; this has not yet been confirmed on every phone. No new permission or library.
- What's new: other countries' independence-day banners are removed. The festival banner now shows only India's festivals and India's Independence Day (15 August). Nothing else was changed in what the banner decides to show.
- What's new: the festival banner no longer draws its own coloured box or border. It is plain text on the app's own background, with the title in the app's own accent colour.

## [1.1.19]

- Patch: voice announcements no longer repeat. Every announcement now goes through one central queue and the same words are never spoken twice within 20 seconds, whatever caused them. The cause of the repeats was that, with a Bluetooth device connected, every announcement was deliberately spoken again on Bluetooth right after the phone speaker. That replay is removed: each announcement is spoken once. A late announcement is possible, a repeat is not.
- Patch: the charger advice now speaks ONLY when charging is slow AND the battery temperature is rising. It stays quiet when the temperature is normal or falling, when the charger has shown it can charge fast, when the battery is at 95% or more (the phone slows charging on purpose near full), and when the level or power is not reported. "Slow charging." is no longer announced at 95% or more. The fixed 40 and 45 degree warnings are unchanged.
- Patch: the "Slow / Normal / Fast charging" change announcement now waits at least 60 seconds after the previous one, even if the charging speed changed again meanwhile, so it cannot keep repeating. The new speed is announced once the 60 seconds are over if it still differs.
- Patch: if slow charging and a rising temperature happen while the phone is plugged into a USB port, the advice now says a file transfer or a low power port may be the cause and that it may not be the charger. This is a guess from the plug type only: Android does not tell an app whether files are being copied, so the app cannot confirm a transfer. No new permission or library.

## [1.1.18]

- Journey mode for long trips: a manual switch in the Saver card that lasts up to 12 hours. While it is on and the phone is not charging, the Saver actions (lower brightness, shortest screen timeout, close background apps, clear notifications, each with its own switch) run right away instead of waiting for the battery level limit, and everything is put back when you plug in or it ends. It never starts by itself and adds no background work or new permission. Honest limit: Android only lets an app end background processes of other apps, so the saving can be small.

## [1.1.17]

- Charging temperature control, early warnings: while charging, the app now watches how fast the battery temperature is rising and speaks BEFORE the 40 and 45 degree warnings, with steps to take (close background apps, take off the case, unplug the charger). If charging is slow for 5 minutes while the phone heats up it also says the charger or cable may be faulty. Honest limit: an Android app cannot cool the phone or fix a charger, it can only warn and suggest. Nothing is said when the phone does not report temperature or power. Uses the existing thermal warning switch. No new permission or library.


## [1.1.16]

- Patch: after an in-app update installs, the downloaded installer file is now deleted automatically when the app starts, so nothing is left in storage.

## [1.1.15]

- New "Saver" (Settings, off by default). When the battery temperature reaches your limit (default 30 °C) or the level falls to your limit while not charging (default 35%), it sets brightness to 10% and the shortest screen timeout, asks Android to close background apps and clears notifications. When the battery is normal again it puts brightness and timeout back. Both limits and each action have their own switch. Music, navigation, calls, messaging, keyboard, launcher and Netra apps are never closed, and it runs at most once per 30 minutes. It shows the real free RAM before and after. Android only allows ending background processes (not Force stop), so the saving can be small. Needs "Modify system settings" for brightness/timeout and "Notification access" for clearing notifications; no new library.

## [1.1.14]

- Patch: the project moved to the Prayagi Store and Services GitHub organization. Update links, the backup update source and the privacy page link now point to the new address.

## [1.1.13]

- Patch: crash reports are now sent automatically. If the app crashed, the next time it opens it sends the report by itself (phone model, Android version, app version and code locations only). The "Send last crash report" button is gone.

## [1.1.12]

- Settings now really shows the "Check for updates" button (status line plus button). It was described in 1.1.10 but the Settings entry was missing from that build.
- New Settings switch "Share anonymous usage count" (on by default): once a day the app adds 1 to a public counter so the Netra Eco site can show approximate active users. Nothing else is sent: no ID, no location, no battery data. You can turn it off.

## [1.1.11]

- Live Power card and Health Insights: values Android does not report (voltage, current, temperature, session estimate, heat time, deep drops, failure risk, capacity health) are now hidden instead of showing "Unavailable". Removed the always-empty "Habit score" badge.

## [1.1.10]

- App updates: the update dialog now has a "Later" button, and if the download or install check fails you now see the exact reason (for example "Allow installs from this source" or a signature mismatch) instead of the dialog silently disappearing. The update stays offered so you can retry.
- App updates, more reliable: if GitHub's anonymous API limit blocks the update check, the app now falls back to a backup source instead of silently showing nothing. Settings has a new "Check for updates" button with a status line (up to date, update available, or the exact failure).

## [1.1.9]

- Battery tab: Voltage, Current and Phone Drain tiles now appear only when Android reports them, instead of showing "Unavailable".
- Festival banner: added Maha Navami (Oct 20) and the end of Durga Puja / Vijaya Dashami (Oct 21) for 2026.

## [1.1.8]

- Announcements now follow one fixed audio rule: they always play on the phone speaker first, and if a Bluetooth device is connected, the same announcement plays there right after. Android does not reliably allow both at the same instant, so this is one after the other. The "Audio Output Routing & Fallback" setting is removed. New "Mute announcements" control: pick 30 min, 1 hr, 2 hr or 8 hr and routine announcements are muted for that time; critical warnings still play.

## [1.1.7]

- Discharging no longer shows a blank screen: while the app is open and the phone is on battery, the Battery tab now shows live discharge data (power draw, current, voltage, temperature, level, time on battery) and a live graph that fills every second. Anything Android does not report is hidden instead of showing "Unavailable", and graph tabs with no data are hidden. Charging and discharging data are kept separate.

## [1.1.6]

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
