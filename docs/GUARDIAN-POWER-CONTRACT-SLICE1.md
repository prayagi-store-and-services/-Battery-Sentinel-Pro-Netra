# Guardian-to-BSPN Slice1: unused pure power-work contract

## Scope

This is the first contract-only port of Guardian's FeatureModePolicy and
MeasuredPowerBudget concepts into BSPN. It is not a copy of Guardian's mode
engine. BSPN's existing central state remains its only state owner.

Added code is internal, stateless and has no production call sites. No consumer,
service, UI, exporter, cleanup action, speech or accessory read is gated by this
PR. It cannot be described as feature enforcement, lower power use or a completed
Power V2 port. No measured budget, numeric age limit or power saving is invented.

## Contract

- Essential work receives only ALLOW_AT_EXISTING_OPPORTUNITY. The caller must
  independently check user consent, permissions, platform execution eligibility,
  lifecycle and recovery requirements. This does not authorize a wakeup or work.
- Optional/decorative work defers on missing live battery data, absent/invalid or
  future monotonic provenance, caller-declared stale state, an existing central
  restriction, Doze/unknown, or invisible decoration. Even otherwise eligible
  optional work defers because no finite measured budget exists.
- No override accepts an arbitrary numeric budget. No feature gains permission.
- CentralPowerWorkContext reads an immutable existing NetraCentralState snapshot.
  It reads existing low-battery/critical flags; it does not calculate new battery
  modes or thermal thresholds, register listeners or change central state.
- BSPN's wall-clock lastUpdateTimestamp is never relabelled as acquisition-time
  monotonic evidence. A future consumer must supply genuine matching monotonic
  acquisition provenance and freshness evidence. No freshness-age or sequence
  guarantee is implied, and no sequence counter/state owner is added here.
- Screen-off confirmation overrides a default screen-on field. Foreground and
  Doze evidence must be explicitly supplied; this port adds no collectors.

## Excluded and next gate

No change to safety/thermal policy or listener lifecycle, critical persistence,
charging ENERGY_COUNTER classification, TTS, Bluetooth, permissions, settings,
files, timers, polling, wake locks, service, schema, dependencies, workflow,
version, signing or release. Existing features keep their current behavior.

Slice2 needs its own visible plan/approval before code. Candidate scope: a single
CSV export path and direct exporter bypass check before records/file work, with
an honest deferral result. Consumer integration must establish provenance and
error/cancellation semantics, rather than silently passing favorable defaults.
No PDF/cache/urgent/manual-action scope is inferred here.

## Verification

Added ten pure policy tests covering missing budget, essential eligibility,
provenance and clock boundaries, restrictions, Doze/visibility, the boolean
matrix and concurrent deterministic evaluation. Five adapter tests cover live
field status, zero/100 boundaries, independent freshness evidence, wall-time
non-substitution, restriction/charging inputs and screen confirmation.

All original tests remain unchanged. Full exact-head Android CI is required
before merge. No local Android SDK/full app run is claimed. Physical device,
energy/drain, thermal safety and runtime consumer enforcement tests are NOT RUN.
This PR has no visual change or new render. No release is part of this PR.

Source inspected: Guardian f2bf39341b276b3f2031aa7b799a486a92981ee7;
BSPN base 9849ed76e4164cac6ff160e5044c99461cfb8384.
