# Security Policy

## Report delivery fix (2.0.11)

Crash and feedback reports from the app now send the website address as the origin header, so the report service accepts them. Reports that could not be delivered before (they stay saved on the phone and retry at the next start) will now go through. Report contents, the stored file and the opt-in text are unchanged. No new permission, library or server.

## Installer file cleanup (2.0.7)

When the app comes back to the front it also deletes installer files older than one hour from its private cache folder. Nothing outside the app's own folder is touched. No new permission, library or network call.

## Update download progress (2.0.6)

The update window now stays open during the download and shows progress. Display only: the download, size check, checksum and signature checks are unchanged. No new permission, library or network call.

## Charging screen brightness and switch (2.0.5)

Two new on-phone settings (screen brightness level and an on/off switch) saved in the app's private preferences. Only the app window brightness changes, no system setting. No new permission, library or network call.

## Brightness restore (2.0.4)

Only the app window brightness override changed (no system setting, no new permission). It now lifts on touch and when the protection ends.

## Update alert tap installs (2.0.3)

The existing update notification now opens the app with a flag that runs the existing verified download and install flow (same SHA-256 and signature checks). No new permission, library or server. The check runs every 6 hours instead of 24.

## Estimate time countdown (2.0.2)

Only the estimate calculation and its label changed. It reads the existing charge target setting. No new permission, network call, library or stored data.

## Shorter announcements and spoken charge target (2.0.1)

Only spoken text and one local alert changed. No new permission, network call, library or stored data. The charge target alert now also speaks through the existing text to speech engine.

## Charging screen design options (2.0.0)
- New choices for the charging screen look: clock, gauge, details style, shown items, colour, gauge brightness and idle brightness. They are saved only in this app's private settings on the phone. Nothing is sent anywhere.
- All values shown are the same real readings as before. A reading Android does not give still shows "Unavailable".
- No new permission, library or network call.

## Charging screen update (1.2.15)
- The charging screen now shows the whole battery percentage that Android reports, colours it by level, and dims the screen to 10% after 15 seconds without a touch. A touch, closing the screen or unplugging the charger restores normal brightness. Only the screen window brightness is changed, and only while this screen is open.
- The charging screen switches are on by default. Opening it over other apps still needs the Android permission Display over other apps, which only the user can grant. Anyone who turned a switch off before keeps it off.
- No new permission, library or network call.

## Plain update failure messages (1.2.15)
- If the update check or download fails, the app shows one plain sentence instead of raw system text. The app's own messages (checksum, invalid APK) are unchanged.
- No new permission, library or network call.

## Festival list cleanup (1.2.14)
- Five non-Indian festival entries are removed from the built-in festival list (Lunar New Year, Passover, Friendship Day, two Hanukkah days; 2026 and 2027). It is a fixed list inside the app; nothing is fetched. No permission, library or network change.

## Charging screen over the lock screen (1.2.13)
- The charging screen can now show over the lock screen (Android activity flag showWhenLocked). It turns the screen on only when the charger is plugged in. It does not unlock the phone and reads nothing from it.
- It opens when the charger is connected, and again each time the screen turns off while charging. The screen-off event is an Android broadcast received only while the existing battery service runs. Opening it from the background still needs the existing Display over other apps permission and the existing off-by-default switch. No new permission, library or network call.
- It closes on a double tap or when the charger is unplugged. Values come from the same source as the Live Power card; missing values show Unavailable. The label "Fast charging" appears only at 15 W or more of measured battery-side power.

## Restricted-settings help (1.2.4)
The Saver card shows a drawn step guide only when Android may block Notification access (app not installed from a store or by this app's updater). It is an illustration, labelled as such. It changes no setting, requests no permission, makes no network call and runs no background work; the "Open app info" button only opens Android's own App info screen.

## Supported Versions

Battery Sentinel Pro Nethra is under active development. The first public release is v1.0.0. Only the latest published release receives security fixes.

| Version | Supported |
| ------- | --------- |
| Latest GitHub release (currently v1.0.0) | :white_check_mark: |
| Older releases | :x: |
| Development builds and CI test APKs | Best effort |

## Reporting a Vulnerability

If you discover a security vulnerability in Battery Sentinel Pro Nethra, please report it privately.

Please do **not** create a public GitHub issue for an undisclosed security vulnerability.

### Preferred Reporting Method

Use GitHub's private vulnerability reporting feature for this repository when available.

If private vulnerability reporting is not available, contact the repository maintainer privately through the GitHub repository/account rather than publicly disclosing the vulnerability.

### Include the Following Information

Please provide:

- A clear description of the vulnerability
- The affected version, release, or commit
- Steps required to reproduce the issue
- Expected behavior
- Actual behavior
- Potential security impact
- Relevant logs, screenshots, or proof-of-concept information when appropriate

Do not include passwords, API keys, authentication tokens, personal information, or other sensitive information in a vulnerability report.

## What Happens After a Report

Security reports will be reviewed and investigated.

When a report is received, the maintainer may:

1. Verify and reproduce the reported issue.
2. Determine the affected components and versions.
3. Assess the security impact.
4. Develop and test an appropriate fix.
5. Release a security update when necessary.
6. Publish appropriate security information after the issue has been addressed.

The exact response time may vary depending on the severity and complexity of the vulnerability.

## Responsible Disclosure

Please allow reasonable time for investigation and remediation before publicly disclosing a security vulnerability.

Public disclosure before a fix is available may increase risk to users.

## Security Scope

Security reports may include vulnerabilities involving:

- Android application security
- Permission handling
- Unauthorized access
- Sensitive data exposure
- Insecure local data storage
- Update and release mechanisms
- Network communication
- Dependency vulnerabilities
- Privilege escalation
- Code execution
- Authentication or authorization
- Other security vulnerabilities directly affecting Battery Sentinel Pro Nethra

## Out of Scope

The following should normally be reported through regular GitHub Issues instead:

- UI bugs
- Feature requests
- General usability problems
- Performance problems without a security impact
- Incorrect battery readings without a security impact
- General Android compatibility issues

If a normal bug also creates a security vulnerability, report it privately as a security issue.

## What the App Does With Your Data and Permissions

This section describes what the current code does. It is updated with the app.

### Live battery telemetry (charging and discharging)
- Voltage, current, temperature, percentage, power and time estimates are read on the device from Android battery APIs (the battery status broadcast and `BatteryManager`). They are not sent anywhere for display.
- The 1-second refresh loop runs only while the Live Power screen is visible.
- Power is calculated battery-side power (voltage x current). It is not wall-adapter wattage.
- Estimates (time to full, time until empty) come only from observed percentage progress. They show `Calculating...` or `Unavailable` when data is missing or inconsistent.

### Network Saving (screen off) suggestion
- The app can suggest switching down from 5G to 4G, or 4G to 3G, after the screen has been off. It uses these permissions:
  - `READ_PHONE_STATE`: to read the current network type. Requested at runtime.
  - Usage Access (`PACKAGE_USAGE_STATS`): to read recent mobile data use. Granted by you in Android Settings. If recent data use is 2 MB or more, no suggestion is made.
- By default the app only shows a notification. A normal app cannot change the preferred network type.
- **Experimental ADB switch:** `WRITE_SECURE_SETTINGS` is declared in the manifest but is not granted by Android to normal apps. Only you can grant it, from a computer, with `adb shell pm grant com.aistudio.batterysentinel.ntra android.permission.WRITE_SECURE_SETTINGS`. Without that grant the switch does nothing. This path is experimental and has not been verified on real devices. Do not grant it unless you understand it, and you can revoke it with `adb shell pm revoke` using the same package and permission.

### Other permissions
The manifest also declares `WRITE_SETTINGS` (Modify system settings), used only by the optional "Charging + screen off savings" to lower brightness while charging with the screen off; Android shows it as a switch that only you can turn on, and the app never grants it. It also declares internet and network state, notifications, boot completed, foreground service, battery-optimization request, approximate and precise location (weather and climate context), Bluetooth, vibration, and `REQUEST_INSTALL_PACKAGES` (for the in-app update flow; Android still asks you to approve every install). Report any permission you think is not needed.

### Solar monitoring (planned, not enabled)
No solar provider is enabled in the app today. Before any provider ships, these rules apply (see `docs/SOLAR_MONITORING_INTEGRATION_PLAN.md`):
- Provider credentials or tokens are stored on the device only, encrypted with Android Keystore-backed storage.
- They must be excluded from Android backup and device transfer. The current backup rule files are still the default templates, so this must be done before solar credentials are stored.
- They are never written to logs, crash reports, analytics, source control or app resources. Provider app secrets are never embedded in the APK.
- Official OAuth or token flows are preferred over collecting passwords. No scraping of private dashboards. Read-only in the first release.
- A provider is listed as supported only after a real, authorized account test returns real data.

### Anonymous usage count (active users)
- Once per UTC day (and once per month) the app adds 1 to a public counter in Firestore (`netra_active/battery-sentinel_<yyyyMMdd>` and `_<yyyyMM>`), so the Netra Eco website can show approximate active users.
- The request contains only the counter document name and "increment by 1". No device ID, install ID, account, location, battery data or app data is sent, and the app keeps no ID for this.
- A local flag stops repeats on the same day. A failed send is retried at the next open. It is on by default and can be turned off in Settings ("Share anonymous usage count").
- Firestore rules allow only creating a counter with value 1 or raising it by exactly 1; the counters are public to read. Anyone could in theory add extra +1s, so the figure is approximate, not exact people. Reinstalling or clearing data can count one person twice.

### Update check
- The app checks GitHub (the public release API, with a backup file on the project website) for a newer version. It sends no user data. Settings has a "Check for updates" button that shows the real status or error.

### Automatic crash reports

- Version 1.2.5: Feedback has a manual "Send crash report" button that shows the exact text first. An automatic or manual send now counts as sent only when the forwarding service answers success=true; before, any HTTP 200 reply was enough and the saved report was deleted. The last crash is kept on the device so it can be sent again.
- Version 1.2.5: Power Preferences use `WRITE_SETTINGS` (user allows it on an Android screen) for brightness, and `WRITE_SECURE_SETTINGS` only if the owner granted it with a computer command; no new permission is declared.
- Version 1.2.9: new Permissions list in Settings. It only reads the status of permissions already declared and opens Android settings pages; no new permission, no network call, no background work.
- Version 1.2.10: wording fix only on the Monitoring screen (Cleanup scope: cache files only). No new permission, no network call.
- When the app crashes, it saves a short report on the device. The next time the app opens, it sends that report by itself, with no button and no question. After a successful send the file is deleted; after a failed send it is kept and retried at the next start.
- The report contains only: phone model, Android version, app version, and the crash stack trace (exception class names and code locations; exception messages are dropped on purpose). It contains no name, email, location, files, device IDs or battery history.
- It goes through the same form pipeline as the website forms (FormSubmit) to the developer's email. The optional "Send feedback" form still sends only when the user presses Send.

### Honest data: no fake values
The app must not invent telemetry. A reading the phone or provider does not give is shown as `Unavailable`, never as a fake zero, a sample value or a guess. Readings differ between phones, and the app does not claim every reading works on every device.

### AI features
Some features call an AI service (Gemini) over the network. Do not enter secrets in AI prompts. Report any case where private data is sent that you did not expect.

## Saver (new in 1.1.15)
- Off by default. When on, and the battery temperature or level reaches the limits the user set, it can lower brightness to 10% and the screen timeout, ask Android to close background apps and clear notifications. It puts brightness and timeout back when the battery is normal again.
- Permissions: `KILL_BACKGROUND_PROCESSES` (normal permission; only ends background processes, it cannot Force stop and cannot touch foreground-service apps), "Modify system settings" (already used by the charging saver), and "Notification access" (granted by the user in Android settings; the listener only calls "clear all" and never reads or stores notification content). A `<queries>` entry for launchable apps lets the app see which apps could be closed; the list stays on the device.
- Never closed: Netra apps, the default phone, SMS, launcher and keyboard apps, common messaging apps and the clock. System apps are skipped. At most one run per 30 minutes.
- No network use, no data leaves the device, no new library.

## Charging temperature advice and charger hint (new in 1.1.17)

- While the phone is charging, the app watches the battery temperature trend and speaks early advice before the existing 40 and 45 degree warnings: close background apps and take off the case when the temperature is rising from 35 degrees, and unplug the charger when it is rising toward the danger zone at 38 degrees. If charging stays slow (under 5 W) for 5 minutes while the phone heats up, it says the charger or cable MAY be faulty.
- Honest limits: an Android app cannot cool the phone or change the charger. This only gives warnings and steps. The charger hint is a hint, not proof. If the temperature or power is not reported by the phone, nothing is said and nothing is guessed.
- Uses the existing announcement switch "thermal warning" (on by default). Permissions: none added. No new library.

## Saver notification access help (changed in 1.2.1)
- On Android 13 and newer a phone can block "Allow notification access" for an app installed from a file (APK); the switch is then grey. The Saver card shows steps for this (App info, three dots, "Allow restricted settings", then notification access) and an "Open app info" button. Android gives apps no way to unlock the block or to tell for sure that it is on, so the steps show whenever notification access is off on Android 13+ and the app was not installed from Google Play or by this app's own updater (which installs through Android's session installer, not blocked per published descriptions). Permissions: none added. No new library. Notification content is never read.

## Announcements and charger advice (changed in 1.1.19)
- All spoken announcements go through one queue. The same words are not spoken twice within 20 seconds. Nothing is replayed on Bluetooth any more. Nothing is uploaded; the text is spoken by the phone's own text-to-speech.
- The charger advice speaks only when charging is slow and the battery temperature is rising. It is quiet when the temperature is normal, when the charger has shown it is fast, at 95% or more, and when level or power is not reported. The fixed 40 and 45 degree warnings are separate and unchanged.
- When this happens on a USB port, the advice says a file transfer or low power port may be the cause. That is a guess from the plug type: Android does not tell an app whether files are being copied. The app does not read file names, storage or the USB data. No new permission.

## Journey mode (new in 1.1.18)

- A manual switch in the Saver card for long trips. It never starts by itself and ends by itself after 12 hours.
- While it is on and the phone is not charging, the existing Saver actions (brightness and screen timeout, closing background processes, clearing notifications; each still has its own switch) run at the next battery reading instead of waiting for the level limit. They are put back when you plug in, turn Journey mode off or it ends.
- Honest limits: it adds no background service, no new permission and no new library; it uses only the battery readings the app already takes. Android does not let an app restrict other apps' data or battery. The only thing possible is to end background processes, and apps may restart, so the saving can be small. Closed apps and cleared notifications cannot be brought back.

## Installer file cleanup
- After an in-app update installs, the app restarts and deletes every downloaded installer file from its cache folder (`cache/updates/`) on start. A new download also replaces older files. If the user cancels the install, the file is removed the next time the app starts.

## Releases and Signing
- Release APKs are built by the repository's guarded release workflow from a reviewed commit on `main` and published on the GitHub Releases page.
- The workflow signs the APK with the project release key and checks that the signing certificate matches the earlier one before publishing. The key is stored as a repository secret and is never committed.
- Install APKs only from this repository's Releases page. CI test APKs are debug builds, signed with a throwaway key, and are not releases.
- Compare the SHA-256 digest shown on the release page with the file you downloaded.

## Dependencies
- Dependabot alerts and updates are enabled. Security alerts are reviewed and fixed through pull requests that must pass the build and tests. Alerts are not dismissed without a reason.
- Build-tool transitive dependencies with open alerts (Netty, Bouncy Castle, Apache HttpClient, Commons Lang, Guava, reached through Android Gradle Plugin test tooling) are forced to patched versions in `app/build.gradle.kts` and the root buildscript constraints. These are build-time only and are not shipped in the APK. Whether GitHub clears the matching alerts is checked after each change; alerts are never dismissed without the owner.
- Some alerts come from build-tool dependencies (Android Gradle Plugin and Gradle plugins), not code shipped in the APK. They are still tracked and fixed.

## Security Principles

Battery Sentinel Pro Nethra follows these principles:

- Use Android public APIs wherever possible.
- Request only permissions required by implemented functionality.
- Do not fabricate permission states or security status.
- Do not expose sensitive information unnecessarily.
- Do not claim security capabilities that the application does not actually implement.
- Show `Unavailable` when a value cannot be read. Never show invented values.
- Keep security-sensitive functionality subject to testing and verification.

## Development Status

Battery Sentinel Pro Nethra is in active development. v1.0.0 is the first published release. Features marked experimental or planned above have not been verified on real devices or are not implemented yet, and should not be assumed to provide production-level guarantees.

## Truth rule for controls (v1.2.2)
A switch or button must change something real on the phone. Three Settings switches (Ultra Battery Saver, Power-saving, Brightness) only stored a flag; they are now locked off and show "Unavailable" with the reason. The Export Summary button that exported nothing is removed. The Saver remembers whether it started because of heat or low battery and restores brightness and screen timeout when that reason is gone. Every shown value needs a real evidence source or shows "Unavailable".

## Standard header (v1.2.3)
The header is the Netra standard: 56 dp, only the app name, the installed version and the device date/time. Everything else scrolls; the bottom bar stays fixed. The header lightning button and the Ultra Battery Saver banner are removed because they only stored a flag. Every shown value needs a real evidence source, otherwise "Unavailable".

## Charging screen and backup (version 1.2.6)

- New optional Charging screen (Battery tab). It shows only real battery readings from Android, on a black screen, and keeps the display on while it is open. It stores one local on/off flag (auto-open when the charger is plugged in, off by default). While the app is on screen it listens for Android's power-connected event, which is event-driven and does not poll. Nothing is sent anywhere.
- Opening it from the background came in 1.2.7 (see below).
- Android app backup is now off (allowBackup=false). Before, the app's saved data could be copied to Google backup with sample rules that limited nothing.
- No new permission, network call or library.

## Charging screen from the background (version 1.2.7)

- New permission: SYSTEM_ALERT_WINDOW ("Display over other apps"). Android requires it to let an app open a screen from the background; only the user can grant it, in Android settings. Without it the switch stays off. It is used for this one screen and nothing else, and no overlay is drawn on top of other apps: the app opens its own full screen.
- The charger-connected event is received by the battery service the app already runs; no new service, polling or wake lock. The only new stored value is the on/off flag for this switch (local, off by default).
- Not possible and not attempted: opening over the lock screen (Android 14 limits full-screen notifications to calling and alarm apps: https://source.android.com/docs/core/permissions/fsi-limits ).
- Android's rules for starting screens from the background: https://developer.android.com/guide/components/activities/secure-bal
- No network call or library added.

## One home screen widget (version 1.2.8)

- The 13 old widgets were empty (they drew nothing), so they and the Widgets catalogue screen are removed. One widget remains: "Netra Sentinel". It shows battery level, charging state, power source and speed, temperature, voltage, current, power, health, a time estimate and the time of the last reading, all from the phone's own battery readings. A value Android does not report shows "Unavailable".
- No new permission, network call, library, timer or alarm. The widget is redrawn only when the battery service (already running) records a reading. If the last reading is older than 15 minutes it says so.
- The widget receiver is exported because Android's launcher must be able to send it update events; it only handles Android's widget update action and has no other entry point. Tapping the widget opens the app.

## Truthful monitoring line in Settings (version 1.2.11)

- Settings no longer says the monitor is an "Ultra-Low Power 24/7 Event-Driven Architecture". The code reacts to Android battery events and also re-checks on a timer (45 seconds, or 15 minutes in the ideal charge state) while the monitor runs, so the old line could not be proven. Text change only: no new permission, network call, library, timer or alarm.

## Battery Saver display restore (version 1.2.12)
- Before the saver lowers brightness and screen timeout, the app saves the old values in its private storage. They are put back when the phone cools down, charging starts or the level recovers.
- If "Modify system settings" is not allowed at restore time, the saved values are kept and the Saver card says Unavailable with the reason. They are restored once the permission is allowed. Nothing is restored in secret and no restore is claimed that did not happen.
- If you changed brightness or screen timeout yourself while the saver was on, your value is kept.
- Closing background apps asks Android to end only background processes of non-system apps (KILL_BACKGROUND_PROCESSES, a normal permission). Android decides what actually stops; apps with a foreground service or that restart themselves can come back. The card reports RAM before and after instead of a promise. Cleared notifications and closed apps cannot be brought back.
- No new permission or library.
