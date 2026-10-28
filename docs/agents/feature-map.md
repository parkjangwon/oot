# oot Feature Map (Screens & UI)

## Screens

| Screen / Dialog | File | Purpose & Key Elements |
|---|---|---|
| **Dashboard (`DashboardScreen`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/DashboardScreen.kt` | Main screen. Top app bar (`oot` title; refresh, add account, settings actions). Top `QuotaSummaryLine` names the vendor and window with the least remaining (errored and stale vendors skipped), its percentage and reset time; the number turns error-colored at 5% or less. Pull-to-refresh `LazyColumn` of `VendorCard`s in the saved order: `VendorMark`, name, severity badge (`Critical` at 5% or less, `Low` at 20% or less), kind chip (`Subscription` / `API credit`), `LimitRow` (`OotProgressBar`, `NN% left`, reset time), `BalanceRow` (amount without a bar), `KeyLimitRow` (OpenRouter key limit with a bar), `ProblemBox` (`Needs attention · reason`), and `Waiting to sync` when no window is available. Long-press drag reorders, with move up/down accessibility actions. If a refresh fails and previous values are kept, the card shows `Values from Nm ago · Refresh failed` and fades the bars. Tapping a card opens `VendorDetailDialog` (provider description, error, delete). Legacy accounts whose provider is no longer selectable show `UnsupportedLegacyCard` (`Not supported`). An empty state banner replaces the list when there are no accounts; otherwise a dashed `AddAccountRow` follows the cards. |
| **Settings (`SettingsScreen`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/SettingsScreen.kt` | Full settings screen, opened from the dashboard gear icon; back arrow and back gesture return. Sections: **Language** — Korean / English segmented control, first so it can be found without reading the current language, applies app-wide including notification text. **Notifications** — persistent notification switch and refresh interval segment (1 / 5 / 15 / 30 / 60 minutes) with a battery note for 1 and 5 minutes. **Display** — 'keep the screen on' switch (off by default); when on, the activity window holds `FLAG_KEEP_SCREEN_ON` while the app is in the foreground and clears it in the background (no permission). **Show in notification** — one switch per account; hidden accounts stay on the dashboard but leave the notification. **Updates** — `UpdateCheckBlock` plus an automatic update check switch (at most once a day when the app opens). **About** — app version and a GitHub source link. |
| **Add Account (`AddAccountDialog`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/AddAccountDialog.kt` | Modal dialog opened from the dashboard add action. Provider dropdown lists selectable `ProviderType`s: OpenRouter, Claude, OpenAI, xAI (Grok), Z.ai (GLM), DeepSeek, Ollama Cloud, OpenCode Go. Account name, link to the provider's key page, then provider-specific sign-in: Claude OAuth browser sign-in with pasted code exchange, OpenAI ChatGPT device flow, xAI device flow; every provider also accepts an API key, with paste/reveal controls and helper text. Optional custom endpoint must be https (inline error, blocks Save). Uses `SecureFlagPolicy.SecureOn` (hidden from screenshots and recent apps) and theme tokens (`DialogCorner`, `ButtonCorner`, `ootInkButtonColors`, `ootOutlinedButtonColors`, `ootTextButtonColors`). |

## Other UI surfaces

| Surface | File | Purpose & Key Elements |
|---|---|---|
| **Persistent notification (`TokenNotificationManager`)** | `app/src/main/java/org/parkjw/apps/oot/service/TokenNotificationManager.kt` | Ongoing notification on a low-importance channel; tapping it opens the app and an action refreshes. Collapsed: custom view with 2 vendors (3 at 412dp and wider) in the saved order, amount or percentage with a small bar. Expanded: up to 8 rows, one per visible vendor — name, `5H` / `7D`, `NN% left` and reset (`2d 3h` keeps the hours) or a balance; stale values get a `(previous)` suffix; errored vendors read `Needs attention`. The notification content text carries a plain-text summary (`Claude 62% · GLM 98% · OpenRouter $12.40`) as a fallback. Accounts switched off in Settings are skipped. Text follows the app language, not the device locale. |
| **Update block (`UpdateCheckBlock`)** | `app/src/main/java/org/parkjw/apps/oot/update/UpdateUi.kt` | Update row rendered in Settings: idle (`Check for updates` with a Check button), checking (spinner), up to date (current version), new version (`Download and install`), download progress, install permission (`Open settings`), and failure states (`Open release page`). `UpdatePolicy` runs the automatic check at most once a day when the app opens. A download is verified against the installed app's signing key before the system installer opens. |

## Theme & Design Tokens

| File | Description |
|---|---|
| `ui/theme/Tokens.kt` | Warm paper/ink color tokens (`LightColorScheme`, `DarkColorScheme`, `OotExtendedColors` including `hairline`, `hairlineStrong`, `inkButton`). |
| `ui/theme/Theme.kt` | `OotTheme` and `OotSpacing` (`xxs` 4dp .. `xl` 32dp, `screenHorizontal` 16dp). |
| `ui/theme/Type.kt` | Pretendard (`OotSansFamily`) and JetBrains Mono (`OotMonoFamily`, `OotNumeric`). |
| `ui/theme/NumericText.kt` | `withNumericSpans` helper applying `OotMonoFamily` to numbers, percentages, and `$` amounts. |
| `ui/theme/ProgressBar.kt` | `OotProgressBar` and `progressLevel` (20% warning / 5% error thresholds). |
| `ui/theme/Shape.kt` | `ButtonCorner` (8dp), `CardCorner` (12dp), `DialogCorner` (16dp), `OotShapeTokens.pill`. |
| `ui/theme/ButtonColors.kt` | `ootInkButtonColors`, `ootOutlinedButtonColors`, `ootTextButtonColors`, `ootIconButtonColors`. |
