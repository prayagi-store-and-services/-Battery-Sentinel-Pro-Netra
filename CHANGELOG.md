# Changelog

All user-facing release notes are maintained here. The guarded release workflow publishes only the exact contents of the current Unreleased section.

## [Unreleased]

- Add guarded GitHub Release automation with signed APK publishing only after the required release-gate checks are green.
- Add in-app GitHub release checking, versionCode-based update detection, cached offline metadata, release notes display, APK checksum verification, and Android user-confirmed installation.
- Externalize release version and signing configuration; no keystore material or passwords are stored in source control.
