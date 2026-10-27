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
