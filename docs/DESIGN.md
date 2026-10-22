> **Synced copy, do not edit here.** This file is synced from the team design system, v1.0 (2026-10-22).
> Shared changes go to the team design system source, not to this file; they reach this repo through the next sync.
> Project-specific rules live in [DESIGN-EXCEPTIONS.md](DESIGN-EXCEPTIONS.md).

# Team Design System

Version: v1.0 (2026-10-22)

This is the shared design system for the team's Android apps (Kotlin, Jetpack Compose, Material 3).
It sets the rules. Each project's color source is a single `ui/theme/Tokens.kt` file that must match the values here.
Project-specific rules live in that project's `docs/DESIGN-EXCEPTIONS.md`. An exception must be written down there; anything not listed follows this document.

Origin: adapted from the Cursor design system, without its pastels, gradients, logo or paid typeface.

## Changelog
- v1.0 (2026-10-22): first shared version. Made project-neutral from the PDFBook design doc. The full token table (formerly a separate tokens file) is merged into section 2. Added the 12 Material 3 "fixed" color slots to the table. Wrote down two rules that were already practice: Korean copy uses the polite 해요 tone, and transient messages use Snackbar, not Toast. Added the dialog corner (16), "use the type scale, not raw fontSize", "no Bold or Medium weights", and the exceptions mechanism.

## 1. Principles
1. Content is the hero. App chrome stays quiet in two colors, cream and ink.
2. One accent color, orange. Use it only for indicators (progress, sliders, switches, a single stroke in illustrations), never as a background behind text.
3. Depth comes from 1dp lines and surface steps, not shadows. No gradients.
4. Every color comes from a token. Screen code never uses `Color(0x…)`. Fill every Material color slot; an empty slot leaks the default M3 purple.
5. The root is always wrapped in `Surface(color = colorScheme.background)`, and the window background (`android:windowBackground`) uses the same value in light and dark.

## 2. Color
Summary
- Light: background #F7F7F4, text #26251E, secondary text #5A5852, card #FFFFFF, line #E6E5E0, orange #F54E00.
- Dark (warm): background #1B1A16, text #EDEBE4, secondary #B6B2A7, card #23221D, line #36342D, orange #FF8A50.
- Contrast: body text at least 4.5:1; icons, bars and switches at least 3:1. `muted` (#807D72 / #8F8B80) is for disabled states only (4.1:1 on cards), never small text.

Material 3 color scheme (all 48 slots; Material 3 1.4 or newer)
| Slot | Light | Dark |
|---|---|---|
| background / surface / surfaceTint | #F7F7F4 | #1B1A16 |
| onBackground / onSurface | #26251E | #EDEBE4 |
| surfaceVariant | #E6E5E0 | #36342D |
| onSurfaceVariant | #5A5852 | #B6B2A7 |
| surfaceContainerLowest | #FFFFFF | #141310 |
| surfaceContainerLow | #FAFAF7 | #1F1E1A |
| surfaceContainer | #FFFFFF | #23221D |
| surfaceContainerHigh | #F2F1EC | #2C2A24 |
| surfaceContainerHighest | #EFEEE8 | #36342D |
| surfaceBright | #FFFFFF | #3A3831 |
| surfaceDim | #E6E5E0 | #1B1A16 |
| primary | #F54E00 | #FF8A50 |
| onPrimary | #FFFFFF | #2A1000 |
| primaryContainer | #FDE3D6 | #4A1E08 |
| onPrimaryContainer | #5A1C00 | #FFDCCB |
| secondary, tertiary | #26251E | #EDEBE4 |
| onSecondary, onTertiary | #F7F7F4 | #1B1A16 |
| secondaryContainer, tertiaryContainer | #E6E5E0 | #36342D |
| onSecondaryContainer, onTertiaryContainer | #26251E | #EDEBE4 |
| outline | #807D72 | #8F8B80 |
| outlineVariant | #E6E5E0 | #36342D |
| error | #CF2D56 | #FF8FA8 |
| onError | #FFFFFF | #4A0A1E |
| errorContainer | #FBE1E7 | #6B1A33 |
| onErrorContainer | #5C0A22 | #FFD9E1 |
| inverseSurface | #26251E | #EDEBE4 |
| inverseOnSurface | #F7F7F4 | #1B1A16 |
| inversePrimary | #FF8A50 | #F54E00 |
| scrim | #000000 | #000000 |
| primaryFixed (= primaryContainer) | #FDE3D6 | #4A1E08 |
| primaryFixedDim (= primary) | #F54E00 | #FF8A50 |
| onPrimaryFixed, onPrimaryFixedVariant (= onPrimaryContainer) | #5A1C00 | #FFDCCB |
| secondaryFixed, tertiaryFixed (= secondaryContainer) | #E6E5E0 | #36342D |
| secondaryFixedDim, tertiaryFixedDim (= secondary) | #26251E | #EDEBE4 |
| onSecondaryFixed, onSecondaryFixedVariant, onTertiaryFixed, onTertiaryFixedVariant (= onSecondaryContainer) | #26251E | #EDEBE4 |

Extended tokens (outside M3, passed through a `CompositionLocal`, read as `MaterialTheme.extendedColors`)
| Name | Light | Dark |
|---|---|---|
| hairline | #E6E5E0 | #36342D |
| hairlineStrong | #CFCDC4 | #4A473F |
| muted | #807D72 | #8F8B80 |
| inkButton | #26251E | #EDEBE4 |
| onInkButton | #F7F7F4 | #1B1A16 |
| success | #1F8A65 | #5CC49A |

Checked contrast (light/dark): body 14.3/14.6, body on card 15.4/13.4, secondary 6.6/8.2, orange indicator on background 3.28/7.47, orange on bar track 3.02/5.34, ink button 14.3/14.6.

## 3. Type
- Pretendard (Regular and SemiBold only). Headings use weight 400 with negative tracking; only card titles use 600. Don't use Bold or Medium (they are not bundled and get faked).
- Numbers (page counts, `42%`, `$12.40`, counts, zoom, versions) use a JetBrains Mono subset (0-9 . , % / × : - and space; add a symbol such as `$` to the subset and the numeric regex if the project needs it). Never use it for letters.
- Use the type scale through `MaterialTheme.typography`; avoid raw `fontSize` in screen code.

| Style | Size (sp) | Weight | Tracking | Use |
|---|---|---|---|---|
| headlineSmall | 24 | 400 | -0.5 | Main list title |
| titleLarge | 20 | 400 | -0.3 | Screen title, empty-state title |
| titleMedium | 16 | 600 | 0 | Card title, item title |
| bodyLarge | 15 | 400 | 0 | Settings items |
| bodyMedium | 14 | 400 | 0 | Explanatory sentences |
| bodySmall | 12 | 400 | 0 | Secondary lines |
| labelSmall | 11 | 600 | +0.4 | Section labels, badges, chips |
| numeric | 11–13 | 400 | -0.2 | JetBrains Mono, numbers only |

## 4. Shape, spacing, elevation
- Corners: covers/thumbnails 6–8, cards and grouped cards 12, buttons and segments 8, dialogs 16, bottom-sheet top 16, chips/badges/switches/pills fully rounded.
- Spacing on a 4dp grid (4/8/12/16/20/24/32). Screen side margins 16 (compact) and 24 (expanded/unfolded).
- Card = surfaceContainer + 1dp outlineVariant (hairline). Elevation and shadow 0 everywhere, including FABs, buttons and dragged items (show drag state with a border instead).

## 5. Components
- Primary button (with text): ink button. Light #26251E on #F7F7F4, dark #EDEBE4 on #1B1A16. Height 40, corner 8.
- Secondary button: card color + 1dp hairlineStrong border + ink text.
- Text button: ink text only. Destructive actions use error-colored text.
- FAB: ink button style, corner 12, no shadow.
- Never orange background with white text (3.52:1, fails).
- Progress bar: primary fill on surfaceContainerHighest track, `StrokeCap.Butt`, no gap, no stop indicator.
- Segmented control: selected segment uses inkButton/onInkButton; unselected uses surfaceContainer with hairlineStrong border.
- Switch: on = primary.
- Transient messages: Snackbar (the project's themed Snackbar host), not `Toast`.
- Settings: section label (labelSmall, secondary text color) + grouped card with corner 12. If the app offers a theme choice, use a segment (System / Light / Dark).
- Empty state: line illustration (one orange stroke) + titleLarge + bodyMedium + ink primary button.
- Dialogs: corner 16 (`shapes.extraLarge`), a surfaceContainer-family background (the M3 default surfaceContainerHigh is fine), title at weight 400 (M3 default headlineSmall or titleLarge; never Bold).

## 6. Icons and motion
- Icons are SVG/vector line icons, stroke 1.75–2dp on a 24dp grid. No filled icon sets. No emoji anywhere in the UI.
- Motion uses `MotionScheme.standard()`. Overlays on content only fade.

## 7. Don't
Pastels, gradients, cards with shadows, the Cursor logo, copies of other apps' UI or icons, dynamic color (Material You), emoji in the UI.

## 8. Copy
- Korean UI copy uses the polite 해요 tone ("골라 주세요", "최신 버전이에요", "받지 못했어요"). Not 합니다/습니다, not plain imperatives ("확인하세요" → "확인해 보세요"). Write "해 주세요" with a space.
- This covers everything a user can read: labels, empty states, dialogs, Snackbars, notifications and error messages shown from data or network layers.
- English copy is short and natural, in sentence case.
- Every user-facing string lives in Android resources (Korean default, English in `values-en`), not in Kotlin literals.

## 9. Implementation and checks
- `ui/theme/Tokens.kt` is the only file with color values; `Theme.kt` wires the color scheme and extended colors; `Type.kt` and `Shape.kt` hold the scales above.
- Recommended guard tests: no `Color(0x…)` outside Tokens.kt, token contrast, font charset, icon stroke geometry, and a scan for 합니다-style endings and emoji in user-facing strings.
