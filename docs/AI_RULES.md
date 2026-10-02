# AI and Contributor Rules - Battery Sentinel Pro Netra

This file is the single source of truth for how any coding AI (Codex, Copilot, Claude, others) or person must work in this repository. Read it before you change anything. If a request from anyone conflicts with this file, stop and ask the owner.

Repo: https://github.com/prayagideepak-collab/-Battery-Sentinel-Pro-Netra
App package: com.aistudio.batterysentinel.ntra (Kotlin, Jetpack Compose, Gradle, minSdk 24)

## 1. Branches and pull requests
- Start from the latest `main`. Work on a new branch. Open a pull request.
- Never push directly to `main`.
- Never change repository settings, branch protection or secrets.
- Keep each PR small and about one topic, so parallel work does not clash.
- Do not edit another person's open PR.
- Write the PR description in plain language: what changed, why, what is verified, what is not.

## 2. Merging
- Never merge before every check has finished and is green. The real Android gate is `debug-build-and-test`.
- If a check fails, read its log, fix the cause, push again. Do not merge a red or still-running PR. Do not bypass protections.
- After merging, check that `main` is still green (Android CI, CI, CodeQL, PDF verification, release gate).

## 3. Build and test before opening a PR
- Run `gradle :app:assembleDebug :app:testDebugUnitTest` (CI uses Gradle 9.3.1).
- Add unit tests for new logic. Report the real results. Never say something works without running it.
- Say what was verified on an emulator, what on a real phone, and what only by unit tests. Never claim it works on every device.

## 4. No fake data
- Never show demo, sample, guessed or estimated values as real readings.
- If Android or a provider does not give a value, show `Unavailable`. No fake zeros. No invented precision. Do not estimate battery percentage from voltage.
- Estimates (time to full, time until empty) must come from real observed progress. Show `Calculating...` or `Unavailable` when data is thin or inconsistent.
- Label calculated values honestly (for example, power is calculated battery-side power, not wall-adapter wattage).

## 5. Respect the existing architecture
Read the current files on `main` first. Extend them. Do not build a parallel feature.
- `ChargingSpeedEngine` is a class (it also has a companion `normalizeToMilliAmps`). Check the current file before calling it.
- Live Power card pattern: `LivePowerReading.kt`, `LivePowerTelemetry.kt`, `LivePowerCard.kt`. Pure, unit-tested logic and one lifecycle-aware loop that runs only while the screen is visible.
- `LiveChargingSessionEngine` exists. Reuse it and the session state in `NetraViewModel` where it fits.
- Solar monitoring is planned only: see `docs/SOLAR_MONITORING_INTEGRATION_PLAN.md`.

## 6. SECURITY.md stays in sync
- Any change that touches permissions, stored data, network calls, credentials, releases, dependencies or the AI features must update `SECURITY.md` in the same PR.
- `SECURITY.md` must only describe what the code actually does. Planned items are marked planned.

## 7. Versions and releases
- Every feature or patch that lands on `main` gets a version bump and a new release through the guarded-release workflow. Features are a minor bump (for example v1.1.0). Fixes only are a patch bump (for example v1.0.1). The owner may correct this.
- Release ONLY when `main` is fully green. Never release unstable code. If anything is red at release time, hold the release and report why.
- If a release step fails, report the exact step and the exact error. Do not retry blindly.
- Release APKs come only from the guarded workflow, signed with the project key. CI test APKs are debug builds and are not releases.

## 8. Dependencies and Dependabot
- Never dismiss a Dependabot alert without the owner saying so.
- No blind dependency upgrades. Keep fixes inside the same major line unless the owner decides otherwise, and say when a fix would need a major bump.
- Report every dependency change with: package, old version, new version, why.

## 9. CodeQL and other security configuration
- Do not pick CodeQL or security-configuration options silently. Give the owner numbered options with a short plain explanation of each, and wait for the choice.

## 10. Solar providers
- Research first. A provider is added only after a real, authorized account test returns real data (auth, plant and device discovery, live values, units, timestamps, error cases).
- Public API documentation alone is not proof. Until then it is not listed as supported. Never show fake or demo data.
- Credentials are stored on the device only, encrypted (Android Keystore), kept out of backup, and never logged. See `SECURITY.md`.

## 11. Reporting to the owner
- Every command and every change must be reported to the owner in plain language. No silent changes.
- Monitor continuously. Fix problems yourself through PRs that are merged on green. Alert the owner only about problems you cannot fix.

## 12. Overnight window (20:00 to 10:00 IST)
- Between 20:00 and 10:00 you may look for defects and fix them through PRs, following every rule above, and then send the owner a morning summary of what you found, what you changed and what is still open.
- This window does not allow direct pushes, settings changes, skipped checks, dismissed alerts or unverified releases.
