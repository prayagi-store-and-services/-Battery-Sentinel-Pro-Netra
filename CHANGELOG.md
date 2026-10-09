# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

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
