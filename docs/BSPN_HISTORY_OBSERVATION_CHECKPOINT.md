# Charging-history observation checkpoint

12 November 2026. Approved list item 1, partial-observation option.

Current repository previously copied the current BatteryRecord onto the old canonical start timestamp after recreation, implying an earlier start level and peak evidence it did not hold. The canonical persisted duration and completed Room history have different evidence boundaries. Live duration behavior from v2.2.32 is unchanged.

The repository now starts a new history portion at the first actual observation timestamp in this process. A completed portion has a known observed start level and peak over retained observations only. Labels say partial, including older rows whose original evidence cannot be reconstructed. No new persistence engine or schema. Earlier missing samples and an interrupted unfinished portion are not recovered, and no whole-session durability is claimed.

Legacy comparison: its static ChargingSession data model exposes separate start/end values, nullable end and sessionStatus. Those fields do not prove restart durability or complete coverage. No private decompiled implementation was copied. Unsafe scoring/encryption/capability claims were not adopted.

Regression-first: five initial tests on unchanged production code had four failures (backdating, recreated start, false one-minute minimum, stale-stop clearing). After patch: eight repository regressions and eleven existing export/Robolectric integration tests pass, plus light/dark production-row render tests. Final CI and real PDF emulator checks remain release gates. Physical installed-update/reboot/OEM/background acceptance is not verified.
