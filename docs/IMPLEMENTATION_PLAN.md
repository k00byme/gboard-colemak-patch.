# Implementation plan

## P0 — Gboard settings integration

Acceptance criteria:
- A clearly named **Gboard Cafe** section appears inside Gboard's own settings.
- The settings entry opens an editor owned by the patched Gboard package.
- The editor persists settings across process restarts.
- No Samsung Honeyboard component is required.

## P0 — Reorderable keyboard editor

Acceptance criteria:
- Show the active supported layout as draggable key tiles.
- Move keys between positions and rows.
- Add/remove supported keys from a controlled key palette.
- Preserve key output, accessibility labels, long-press behavior and alternate characters.
- Save per-layout configuration and restore defaults.
- Unsupported layouts must be left unchanged rather than corrupted.

First implementation target should be the Latin alphabetic layout. Expanding to symbols, numbers, emoji or non-Latin scripts requires separate validation.

## P0 — Effects engine

Acceptance criteria:
- Effects run on key interaction and are rendered by Gboard's own view lifecycle.
- At least one effect can be selected and previewed from Gboard Cafe settings.
- Effects support enable/disable and configurable color/intensity where meaningful.
- Animation is bounded and does not retain Activity/View references after keyboard dismissal.
- No external Samsung library/service is required.

## Effect porting policy

For every HoneyTea effect:
1. Attempt a behavior-level port only if the required code is separable from Samsung APIs and licensing permits reuse.
2. Otherwise implement an independent equivalent using Android drawing/animation primitives and Gboard hooks.
3. Record status as `ported`, `recreated`, `blocked` or `not-priority`.
4. Do not mark a feature complete until it is visible and tested on-device.

## P1 — Other Keys Cafe features

Only after both P0 priorities are stable: sound customization, theme extras, gesture options, statistics and typing practice.

## Validation matrix

- Exact Gboard 18.5.4 arm64 build.
- Samsung Galaxy A15 5G / Android 16 as the first device target.
- Gboard settings opens without crash.
- Keyboard opens in common text fields.
- Layout changes survive force-stop/restart.
- Effects stop cleanly when the keyboard closes.
- No regression to voice input, suggestions, clipboard, emoji panel or one-handed mode.
