# oot Feature Map (Screens & UI)

## Screens

| Screen / Dialog | File | Purpose & Key Elements |
|---|---|---|
| **Dashboard (`DashboardScreen`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/DashboardScreen.kt` | Main screen (Option A list cards). Top app bar (`oot` title, refresh, add account, settings gear). Top `QuotaSummaryLine` showing the least-remaining quota window (`가장 적게 남은 곳 · 벤더 창 NN% · 리셋`). Pull-to-refresh list of `VendorCard`s (1dp hairline, 12dp radius, zero shadow) with `LimitRow` (`OotProgressBar`, `NN% 남음` in `OotMonoFamily`, time to reset), `BalanceRow` (`$NN.NN 남음` without bar for OpenRouter), `ProblemBox` (`확인 필요 · 사유` for errored accounts), `UnsupportedLegacyCard` (`지원 안 함` for accounts whose provider is not selectable), and dashed `AddAccountRow`. Tapping a card opens `VendorDetailDialog` for account details/deletion. |
| **Settings (`SettingsPlaceholderScreen`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/DashboardScreen.kt` | Placeholder settings screen reachable from the dashboard top bar gear icon. Controls foreground status bar monitoring toggle and refresh interval (5 / 15 / 30 / 60 minutes). |
| **Add Account (`AddAccountDialog`)** | `app/src/main/java/org/parkjw/apps/oot/ui/screens/AddAccountDialog.kt` | Modal dialog to connect AI provider accounts (Claude OAuth / API Key, Z.ai GLM API Key, xAI Device Flow / API Key, OpenRouter API Key, OpenAI API Key, Cursor token). Styled with `OotTheme` tokens (`DialogCorner`, `ButtonCorner`, `ootInkButtonColors`). |

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
