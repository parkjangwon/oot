# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-10-21

> **Note:** This release is signed with a new oot release key. If your installed oot was signed with a different key, uninstall it and install v1.1.0; accounts must be added again.

### Added
- CI workflow for automated build, unit tests, lint checks, and release version validation.
- Remaining-amount domain model (`VendorUsage`, `UsageWindow`, `Remaining`, severity levels).
- In-app update support (GitHub release check, APK download, signature verification, and system installer launch).

### Changed
- Notification redesign for clearer remaining token and quota status display.
- Dashboard redesign for improved readability and account status overview.
- Antigravity provider hidden from new account selection (`isSelectable = false`).

### Fixed
- Claude 5-hour (`five_hour`) and weekly (`seven_day`) usage window parsing, error mapping, and 429 rate-limit caching.
