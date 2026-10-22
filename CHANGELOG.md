# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- English UI. Korean stays the default; pick 한국어 / English under Settings → '언어 · Language'. The choice is saved, applied on start and also offered in the system's per-app language settings (Android 13+). Notifications, card errors and empty states follow it (#74).

### Changed
- Every user-facing string now lives in string resources (`values` Korean, `values-en` English); lint (`HardcodedText`, `SetTextI18n`, `MissingTranslation`) and a source-scan test fail the build on hardcoded UI text (#74).
- The notification channel name and description are now in the app language ('AI 토큰·한도 모니터' in Korean) (#74).

### Fixed
- A dashboard reset time under a minute no longer reads '곧 리셋 리셋' (#74).

### Removed
- Google Antigravity provider: its quota is only available through Google's private API, which this app's own OAuth client cannot use (#53). A saved Antigravity account is dropped on update; other providers keep their order and settings (#73).

## [1.2.1] - 2026-10-22

### Added
- Settings: a per-provider '알림에 표시' toggle (default on). Providers switched off leave the notification but stay on the dashboard, and the choice survives restarts (#56).

### Changed
- The notification labels windows '5H' and '7D' and puts the reset time after a middle dot, e.g. '7D · 2일 3시간 후'; app screens keep '5시간'/'주간' (#58).
- The empty notification tells 'all providers hidden' (go to Settings) apart from 'no providers registered'; the dashboard empty-state title is now '등록된 프로바이더가 없어요' (#56, #67).

### Fixed
- The drag-reordered provider order is saved and survives a force-stop, a relaunch and a foreground-service restart; the notification follows it. A new provider is added at the end, and deleting one does not reshuffle the rest (#61).
- The Z.ai letter tile shows 'Z' instead of 'G' on the card and in the notification (#54).

## [1.2.0] - 2026-10-22

### Added
- Long-press and drag to reorder vendors; the order is saved and both notifications follow it (#18).
- OpenAI: sign in with ChatGPT (OAuth device flow) and show 5-hour and weekly limits (#22).
- New providers: DeepSeek (balance), Ollama Cloud and OpenCode Go (#24, #28, #26).
- Google Antigravity is back, with Google-model and third-party-model usage; the notification shows the Google value (#29).
- OpenRouter: account credits plus the key's own limit (#21).
- xAI: weekly reset time on the card and in the notification (#30).
- 1-minute and 5-minute refresh intervals, run in the notification's foreground service, with per-vendor back-off on HTTP 429 honoring Retry-After (#35, #49).
- New app icon (remaining-ring gauge) and notification small icon (#31).
- Official vendor logos where the brand terms allow it (#32); sources in `docs/third-party-logos.md`.

### Changed
- Z.ai (GLM) shows both 5-hour and weekly limits (#19).
- The notification shows the 5-hour value for vendors that have one, with reset times on expanded lines (#20, #15).
- A remaining number at 20% or less keeps the default color; only 5% or less is red (#15).
- Wording: '계정' → '프로바이더'; the Settings 'Source' item is now 'GitHub' (#34, #37).
- README for v1.2.0 (#33).

### Fixed
- Back from Settings returns to the dashboard; press back twice to exit the app (#37).
- Antigravity token exchange (#29).

### Removed
- Cursor provider; stored Cursor accounts are dropped safely on upgrade (#23).

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
