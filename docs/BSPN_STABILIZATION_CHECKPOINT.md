# Stabilization checkpoint: charging screen and test alerts

This change fixes two independently reproduced defects. It does not add a new engine or change the release version.

## Changes

- Route charging-screen broadcasts by event type. `SCREEN_OFF` is not a battery snapshot and must not be treated as unplugging because it lacks battery extras. Actual unplug or confirmed charging stop keeps the optional lock behavior. Mode restrictions close the charging screen without requesting device lock.
- Give target-alert tests their own notification ID. The test previously used the same ID as the live overheat warning and replaced it. Existing status/alarm/overheat/threshold IDs stay unchanged.

## Verification

- Local Android debug Kotlin compilation: passed.
- Targeted Gradle tests: 41 passed, zero failed, zero skipped. Includes 13 new tests and 28 existing charging guard/target/repeat tests.
- Counterfactual baseline: with the original Activity and TargetAlert restored, the screen-off and live-overheat-preservation integration tests failed. Restoring this patch returned the targeted suite to green.
- Git whitespace check: passed.
- GitHub Actions: not run for this checkpoint yet.
- Physical phone, OEM charging/background behavior, visual acceptance: not run. Robolectric does not establish those outcomes.

## Outside this change

Sustained-slow/night display policy, timer consolidation across current screens, persistence of alarms/mutes, repository session recovery, schema migrations and wider Legacy feature comparison remain separate work. No watchdog, settings-default change, UI redesign, release signing, merge or release is included.

## Second local batch: canonical display duration

The live current card/black charging screen and the Live Charging Monitor now accept the Central Unit's existing persisted start, for a matching charging/discharging state. No fourth preferences file or new session store is added. Invalid, nonpositive and future starts are rejected; when no valid central start exists, the first observed mode remains the fallback.

A restored epoch is converted once to an elapsed-realtime anchor, including negative anchors after a reboot. Clock changes after binding do not repeatedly recalculate age. A new canonical session identity clears sampling/ETA evidence rather than joining different sessions. Restored duration is not restored measurement history: ETA learning starts with new observations.

Charge-curve preferences initialization is moved into the existing `initPersistence(context)` setup, rather than reading a global Application from every raw sample. Production already calls that setup; standalone restore tests no longer require the whole Application's background services/network startup.

Additional verification:

- 12 new canonical validation/recreation tests, one cross-engine Central Unit restore test (charging and discharging), and two actual production-component light/dark render tests.
- Wider targeted set including existing session restore, ETA, live telemetry, live monitor, discharge anchor and first reliability batch: passed. This is still not the entire suite.
- Rendered production LivePowerContent inspected in light and dark at a 360dp-wide fixture. `Session Duration: 02:00:00`, fresh `Calculating...` ETA and the revised saved-start explanation are readable, with no clipped card text in this fixture. Test data is labelled, not presented as a live phone reading.
- No installed-update, real reboot, OEM background or physical-device acceptance claim. Repository charging-session history recovery and schema migrations remain separate pending work.
