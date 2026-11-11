# Pecalang interface

## Direction

A native Android adaptation of [Zenos](https://zenos-ui.pages.dev/) and
[Apple's Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/).
Zenos is a React library; its JavaScript components are not embedded in the Kotlin
app. Its semantic-token system, neutral surfaces, SUSE headings, compact controls,
subtle borders and restrained brand accent inform the Compose implementation.

The prior mint/lavender canvas is replaced by obsidian, graphite and warm ivory.
A small vermilion brand marker connects the identity to Zenos without making
status indicators compete with a brightly colored interface.

## Brand and typography

- App name: **Pecalang**, in launcher, report, settings, notifications and new exports.
- Mars wordmark: the actual locally installed `Mars.otf`, bundled offline. It is
  OpenType rather than a renamed `.ttf` substitute.
- Launcher monogram: the P outline extracted from the same Mars font; adaptive
  and monochrome icons are included.
- SUSE Bold headings, as used in Zenos; system sans serif for reading and numbers.
  SUSE's Open Font License is included in app assets.
- Existing application ID, persisted preferences and report data remain compatible.

## Layout and behavior

- Fixed wordmark/settings header; centered reading width on wider screens.
- Compact asymmetric score summary with a fine segmented dial, status text,
  check timestamp and explicit partial-check feedback.
- Separate metrics for checks, bundled indicators and coverage gaps.
- Grouped, severity-sorted priorities with full-row navigation and named severity.
- Optional AI review below priorities, visibly advisory and separate from scoring.
- Coverage gaps and collection failures remain available in the report.
- Persistent neutral primary action within a restrained translucent control dock.
- Solid reading surfaces; glass-inspired treatment belongs to the control layer.
  This is not Apple's proprietary Liquid Glass renderer.
- Settings use semantic radio/switch groups, staged changes and explicit Save.
- API-key visibility control, keyboard-aware layout, connection status feedback,
  expandable tool information, and an explicit AI/watch off action.
- Scrollable details with distinct observation, consequence and recommended action;
  separate copy and Android-settings actions.

## Accessibility

System text scaling, native back behavior, 48dp-or-larger actionable controls,
full-row radio/switch semantics, labeled icon buttons, screen-reader headings,
score description and live connection feedback are retained. Status includes text,
not just color. Reduce Transparency is persisted locally. No perpetual decorative
animation is introduced. Physical-device and full TalkBack testing remain release QA.

## References

- [Zenos theme customization](https://zenos-ui.pages.dev/foundations/tokens/)
- [Zenos playground](https://zenos-ui.pages.dev/playground/)
- [Apple materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [Apple layout](https://developer.apple.com/design/human-interface-guidelines/layout)
- [Apple typography](https://developer.apple.com/design/human-interface-guidelines/typography)
- [Apple accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)

## Feature review

The separate implementation audit and its evidence are in [FEATURE-REVIEW.md](FEATURE-REVIEW.md).

## Verification

- Final integrated debug APK builds successfully; all 34 unit tests pass.
- Android 15 emulator: report and settings reviewed in light and dark appearances;
  overview reviewed at 150% system text size with a stable wordmark and adaptive score layout.
- Offline AI smoke test: saved the built-in model through Settings, ran the review,
  verified two AI findings, no AI error, and an unchanged rule score.
- Android lint: one retained package-inventory permission review finding; no UI
  or API-level compatibility errors. No paid-provider requests were made.
