# Guardian compatibility port, 18 November 2026

Guardian stays a separate application. This inventory compares Guardian main
8d73be807babeadaccdecb6f4ef9dceca7cf4da4 with BSPN main
11cf635cda44922d7f1c1daa1020d5898d5cb171. No release is included.

## Port 1: applied-value recovery

Guardian cooling recovery retains a previous applied brightness when a later
requested target fails. BSPN already has the broader, shared SavingSettings
owner and consent ledger. Port that recovery rule into the existing owner,
including timeout fields, rather than adding a second controller. The original
baseline stays unchanged. Failed rollback retains the witness for retry; a
subsequent different manual value takes priority. Default consent stays OFF.
No thresholds, UI, services, permissions or telemetry behavior change.

Regression cases cover rejected later brightness and timeout targets, a failed
rollback followed by retry, and a manual edit while recovery is pending.
Physical-device acceptance is not run by these unit tests.

## Keep BSPN implementations, do not copy duplicate engines

- Thermal policy/controller, severity thresholds and existing cause investigator.
- SavingSettings display/timeout/sync ownership and separate SavingConsent.
- Existing media-aware announcement engine and Bluetooth announcement policy.
- Existing lifecycle polling controller, telemetry model, charts and reports.
- Existing widget. The separate request to add a widget to Guardian is not a
  second BSPN widget.
- Learned charging ETA is already in BSPN PR323. Guardian's simpler observed
  session estimator must not replace or duplicate that pending work.

## Not portable as data

Guardian's Room schema, historical samples, settings and private diagnostics
belong to a separate app sandbox. This code port neither reads that sandbox nor
claims a historical data migration. An explicit import format and user-selected
file would be needed before offering cross-app history transfer.

## Further review, not implemented in this PR

Review power-tier scheduling, bounded local diagnostics, recovery backoff and
remaining unknown-sensor handling against BSPN's existing owners. Do not copy
Guardian's whole UI, dependency-injection graph, thermal thresholds, background
service stack or unmeasured power-impact claims. Any visible change needs a
preview and every BSPN main merge needs its own approval.
