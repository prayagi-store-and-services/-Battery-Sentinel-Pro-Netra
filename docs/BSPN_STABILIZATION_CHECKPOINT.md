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
