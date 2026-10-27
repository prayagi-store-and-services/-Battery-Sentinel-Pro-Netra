# Guava alert 55 dependency scope

Advisory: https://github.com/advisories/GHSA-xxph-c9ww-hj94

CVE-2026-102554 affects Guava native Java deserialization through 33.7.1. The advisory names 33.7.2 as patched. No alert was dismissed.

## Resolved scope before this change

Commands run against the 2.2.14 source:

- `:app:dependencyInsight --dependency com.google.guava:guava --configuration releaseRuntimeClasspath`: no Guava dependency.
- Same command for `debugAndroidTestRuntimeClasspath`: no Guava dependency.
- Same command for `debugUnitTestRuntimeClasspath`: 33.4.8-jre via Robolectric 4.16.1; error_prone_annotation requests 33.4.0-jre, resolved to 33.4.8-jre.
- Root `buildEnvironment`: existing buildscript constraint already resolves Guava to 33.7.2-jre.

The alert's settings.gradle.kts location is not evidence that the production APK bundles the affected library. This fix targets the vulnerable JVM unit-test configuration, not a claimed Android runtime vulnerability.

## Change and limits

Add a testImplementation dependency constraint for 33.7.2-jre. It does not introduce Guava into the app's production runtime. Keep newer compatible versions eligible for resolution. Do not add a production dependency merely to silence the alert.

Before merge, confirm debugUnitTestRuntimeClasspath resolves 33.7.2-jre, production and instrumentation runtime remain without Guava, and run the full unit suite and CI. Reconcile the live alert after dependency submission; do not call it closed before GitHub reports it closed.

## Follow-up after PR #258 (2.2.15 main 833cdaf)

Alert 55 remained open. A complete local resolution audit (all 61 resolvable app configurations, no unresolved dependencies) found additional host-tool copies:

- KSP: four ksp*ProcessorClasspath configurations and internal debug/release KSP classpaths selected 33.2.1-jre via Room compiler processing/Moshi codegen.
- UTP: thirteen independent unified-test-platform configurations selected 32.0.1-jre or 33.3.1-jre.
- Android-test lint checks selected 33.4.8-android.
- Unit-test compile/runtime already selected 33.7.2-jre. Root buildscript already selected 33.7.2-jre.
- Debug/release compile and runtime, and Android instrumentation runtime, had no Guava. Host UTP is distinct from the on-device instrumentation runtime.

The follow-up adds constraints only to the existing host KSP declaration bucket, independent UTP configurations and lint-check classpaths. Constraints do not add Guava when no dependency requests it, do not force a downgrade, and leave Android runtime dependencies unchanged. No application source or version is changed; this is a build/test dependency fix, not an APK runtime security fix.

After the constraints, every Guava-bearing app configuration resolves 33.7.2-jre and all Android runtime configurations still have no Guava. All configurations resolve without missing dependencies. `scripts/guava-audit.init.gradle` repeats the audit in CI, fails unresolved graphs or vulnerable selected versions, and rejects new Guava edges in Android runtime. Full tests and emulator instrumentation must still pass before merge. Alert closure remains a live GitHub check after main dependency submission, never inferred from this document.

The audit also verifies the root buildscript classpath and asserts named KSP/unit-test/lint configurations plus at least thirteen UTP configurations and their Guava edges. Renamed or missing expected configurations fail rather than producing a vacuous pass. The workflow triggers on pull requests to main and manual dispatch. Its job is named graph. Required-check status is repository configuration, not set by this patch or claimed here.
