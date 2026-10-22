# oot design exceptions

Project-specific rules on top of the team design system (`docs/DESIGN.md`). Anything not listed here follows `docs/DESIGN.md`.

## Vendor logos (approved exception)
The general rule forbids copying other apps' icons. oot may show official AI vendor symbols to identify providers.
- Symbols only, no wordmarks, used exactly as provided (no recolor or remix), except official mono variants tinted with `onSurface` or the notification text color.
- Vendors whose guidelines need permission (currently OpenAI and Z.ai) use a letter tile instead of a logo.
- Every logo's source, date and license note is listed in `docs/third-party-logos.md`. Add a row there before adding a logo.

## Quota levels
- Bars and numbers show what is left (remaining, not used).
- At or below 5% remaining, the bar and number use `error`. At or below 20%, only a "주의" badge on the dashboard; the bar stays orange. Dollar balances have no default warning.
- `OotProgressBar(progress, level)`; `progressLevel(remainingPercent)` holds the thresholds.

## Numeric font
- The numeric regex and font subset include `$` for balances such as `$12.40`.

## Ongoing notification (RemoteViews)
Compose tokens can't reach RemoteViews, so the notification uses color resources that mirror the tokens:

| Resource | Light | Dark (`values-night`) | Token |
|---|---|---|---|
| notif_bar_primary | #F54E00 | #FF8A50 | primary |
| notif_bar_danger | #CF2D56 | #FF8FA8 | error |
| notif_bar_track | #EFEEE8 | #36342D | surfaceContainerHighest |
| notif_text_primary | #26251E | #EDEBE4 | onSurface |
| notif_text_secondary | #5A5852 | #B6B2A7 | onSurfaceVariant |
| notif_text_danger | #CF2D56 | #FF8FA8 | error |

- Keep these in sync with `Tokens.kt` when token values change.
- Collapsed (`notification_collapsed.xml`, custom content view): one row, least remaining first. 2 items under 412dp, 3 items at 412dp and up (`notification_collapsed_items`). If any account has a problem, one "확인 필요" item comes first (no bar). Bar height 4dp with 14dp marks; the collapsed bar width may flex.
- Expanded (`notification_expanded.xml`, big content view): every account in the user's order, one line each: 16dp mark, name, window, bar (fixed 64dp), remaining, reset. One "새로고침" action.
- Style `DecoratedCustomViewStyle`; only RemoteViews-safe views (LinearLayout, ImageView, TextView, ProgressBar). Window labels may be shortened to 5H/7D in the notification only; the app keeps "5시간/주간".
- Never show emoji prefixes, usage %, model names, emails or update news in the notification.

## Layout
- Phone-only today: one side margin, 16 (`OotSpacing.screenHorizontal`).
- Theme follows the system (no in-app theme choice yet).

## Naming
- Extended colors class: `OotExtendedColors` via `LocalOotColors`.
