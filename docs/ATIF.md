# ATIF v3.0 - applicable parts for Battery Sentinel Pro Netra

ATIF v3.0 (Absolute Truth & Intelligence Framework) is the owner's permanent master rule, issued 9 Nov 2026. Canonical full text: private governance copy (`netra-intruder`, `docs/ATIF-v3.0.md`). Per the owner's instruction, this file lists only the parts that apply to THIS app, kept separate from other apps.

## Applicable parts and why

- Part 01 (Identity & Mission), 02 (Absolute Truth): all battery data shown must be accurate before comforting.
- Part 04 (Fact Classification), 05 (Confidence): readings are VERIFIED FACTS only when they come from Android APIs; estimates stay labelled as estimates.
- Part 06 (Zero Hallucination): no invented battery figures, cooling claims or health promises.
- Part 09 (Real-Time Verification): "current release" and version references must match the published release when written.
- Part 14 (Transparency): limits of Android/OEM background behaviour stay visible.
- Part 15 (High-Stakes Protection): heat and charge-target warnings are safety-relevant; they are observations, never guarantees.
- Part 16 (Privacy & Financial Security): readings and history stay on the phone.
- Part 17 (Response Quality), 20 (Netra Governance): descriptions, changelogs and site copy must match verified behaviour.

## Data this app shows, and its status

- Battery/charging readings: from Android BatteryManager - VERIFIED source, device variance possible.
- Heat and charge-target warnings: threshold observations. They do NOT stop charging at a hardware limit and do NOT guarantee cooling, battery life or battery health.
- Last full-charge session: observed timestamps only; gaps, clock changes and restarts break continuity (shown honestly).
- CSV/PDF exports: only recorded readings and sessions.
- Bluetooth battery percentage: shown only when the phone and device actually report it.

## Standing rule

Any new feature's copy enters the app, README, WHATS_NEW and site only after it is verified in code; planned work stays labelled planned; automated test results are never presented as physical-device acceptance.

## Source labels (owner-approved 9 Nov 2026)

Every user-visible data claim carries a short source label. UI strings stay short; the label for each claim lives in this file's "Data this app shows" list and in WHATS_NEW/release notes (e.g. "Source: Android BatteryManager"). In-app visible source labels are planned and ship only after the owner's UI preview.

## Stale data: OLD marking (owner-approved 9 Nov 2026)

Any dated or versioned claim that stops matching reality is marked "OLD (as of <date>)" at the next check and corrected in the same or a following PR. Stale claims are never silently edited and never left unmarked.

## Weekly audit (owner-approved 9 Nov 2026)

A weekly audit records each material claim as VERIFIED or NOT VERIFIED in `docs/ATIF-AUDIT.md`, newest first. Failures and NOT VERIFIED items are listed first in every audit and every report.
