# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-10-23

Deleting an account now also signs it out at the provider where that is possible, your saved keys move to newer encryption without signing in again, and usage cards cope better with incomplete replies.

### Added
- Deleting an account that was added by signing in to OpenAI or xAI also asks the provider to revoke that sign-in. For Claude, and for OpenAI, xAI, OpenRouter and DeepSeek API keys, the key is removed from this device only, and the account dialog says to revoke it in the service itself. Deleting always finishes on the device, even when the provider cannot be reached (#118).

### Changed
- Saved keys and sign-ins move to a newer encryption method on the first launch after the update. This happens once and you stay signed in (#117).
- If the encrypted store cannot be opened, the dashboard shows a card that offers to reset it and add accounts again, after a confirmation. The reset keeps your language and other settings (#115).
- Settings works better with a screen reader: the back button is read as 'Back' and section titles are headings. Texts use a real ellipsis (…) (#128).
- An unused sign-in link was removed. Signing in works as before (#116).

### Fixed
- OpenRouter keys that cannot read account credits show the key's remaining limit instead of an error (#131).
- Claude and OpenAI cards show only the usage windows the provider reports. A missing window no longer shows as 0% or 100%, and one bad reply no longer stops other accounts from refreshing (#132).
- DeepSeek, OpenCode Go and Ollama Cloud replies with empty fields no longer break the card (#137).
- A request count such as 'Requests left: 4999' is no longer shown as a $4999.00 balance (#136).

## [1.3.2] - 2026-10-22

### Changed
- Credentials are only ever stored in the encrypted store. Data kept by older versions is moved into it once on update, so users stay signed in. If the store cannot be opened, the app runs from memory for that session and says so (#98).
- Backup and device transfer exclude the credential store, so a restored device starts clean and asks you to sign in again instead of getting an unreadable store. Other settings are still backed up (#99).
- Network traffic is https only. A custom endpoint must be an `https://` address, and an account that already stores an `http://` endpoint shows an error instead of sending requests (#101).
- The add-account dialog is hidden from screenshots, screen recordings and the recent-apps preview. Key and code fields use a password keyboard without suggestions, and copied sign-in codes are marked sensitive on Android 13+ (#102).
- The collapsed notification no longer adds '(이전값)' / '(previous)', so the bars keep their width; the expanded notification and the cards still mark stale values. Stale cards draw their usage bars dimmed (#108).
- The English add-account button reads 'Get a key in Claude Console' and fits on one line (#108).

### Fixed
- Korean words no longer break in the middle in the add-account dialog (#97).
- An account removed while its token was being refreshed no longer comes back (#100).
- xAI weekly usage is read as a percentage: right after a weekly reset it no longer shows 0% left. When a refresh fails, cached values are marked with how old they are, and windows past their reset time are dropped (#106).

## [1.3.1] - 2026-10-22

### Changed
- Korean copy in the add-account dialog, card and toast error messages, the unsupported-provider card and the dashboard empty state uses 해요체; English copy got the same plain, short tone. A test keeps 합니다체 out of the Korean strings (#85).
- The add-account dialog has no emojis any more (helper lines, xAI and Claude box titles); a test keeps emojis out of all strings (#86).
- Docs: synced copy of the design system v1.0 (`docs/DESIGN.md`) and the oot-specific rules (`docs/DESIGN-EXCEPTIONS.md`), linked from the README (#87).

## [1.3.0] - 2026-10-22

### Added
- English UI. Korean stays the default; pick 한국어 / English under Settings → '언어 · Language'. The choice is saved, applied on start and also offered in the system's per-app language settings (Android 13+). Notifications, card errors and empty states follow it (#74).

### Changed
- Every user-facing string now lives in string resources (`values` Korean, `values-en` English); lint (`HardcodedText`, `SetTextI18n`, `MissingTranslation`) and a source-scan test fail the build on hardcoded UI text (#74).
- The notification channel name and description are now in the app language ('AI 토큰·한도 모니터' in Korean) (#74).
- English status badges read 'Low' / 'Critical' instead of 'Warning' / 'Danger' (#80).
- The empty-state notification title uses the lowercase wordmark: 'oot · 토큰 모니터' / 'oot · token monitor' (#80).

### Fixed
- A dashboard reset time under a minute no longer reads '곧 리셋 리셋' (#74).

## [1.2.2] - 2026-10-22

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
