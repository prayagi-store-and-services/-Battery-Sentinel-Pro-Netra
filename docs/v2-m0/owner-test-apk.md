# BSPN owner test APK path, Q107

21 November 2026. Workflow proposal only. No APK built, no signing run, no merge, dispatch, tag, GitHub Release, store upload or production rollout is approved by this PR.

## Purpose and source

Build one clearly test-labelled APK from an exact reviewed green main SHA after separate approval. Includes the already merged ETA policy, saving rollback witness, owned thermal callback changes and unused power contract plus M0 docs. The unused contract is not an active four-mode engine. The test is not proof of battery savings, temperature improvement or M0 completion.

The existing workflow currently overrides the name with 1.0.0-test and prints rather than asserts package metadata. This PR requires the reviewed SHA, labels the test from the current main version plus SHA, asserts package/version/minSDK and existing signing certificate continuity, removes the temporary signing file even on failure, and keeps the APK/evidence as a three-day Actions artifact only. No signing secret is added, changed, copied to a person or committed.

## Install and rollback warning

The intended APK has the same package and signing identity as the installed BSPN app. It replaces that app rather than installing beside it. The versionCode stays at main's current value; there is no versionCode jump. A lower installed version, data migration or OEM rule can still affect installation and rollback. Do not uninstall or clear data to force installation. A verified backup/restore and rollback plan must be agreed before a real installation where data matters. Android installation is not performed by the workflow.

## Separate decisions still required

1. Review and approve merging this workflow PR after exact-head checks pass.
2. Review the then-current main SHA and approve one test workflow dispatch using existing signing secrets.
3. Independently verify the downloaded APK signature, package, version and SHA256 against the recorded exact source before delivering it privately to the owner. No public APK link or production release is implied by a public repository artifact.
4. Owner chooses when to install after the replacement/data warning and checks the device. Keep Play Protect on. Do not bypass a warning or uninstall a working app merely to force an update.
5. Review the owner's findings and the separate device/power/artifact gates before any production version/release PR or rollout.

## Device evidence to capture

Record device/OEM/Android version, installed/current test APK identity, charging source, starting battery/temperature, screen/network state and test duration. Check ETA transitions and unavailable/stalled inputs; thermal status unavailable/stop/restart paths; saving settings ownership and restoration after manual changes; ordinary alerts and media-skip behavior. Do not test by intentionally overheating, discharging unsafely or changing protected system settings. Stop on unexpected behavior. Existing M0 device and power measurement tables remain OPEN until actually measured.

No physical device or power acceptance is claimed by passing workflow syntax, CI, signing or an artifact upload.
