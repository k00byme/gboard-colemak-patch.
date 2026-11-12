# Implementation plan

## Patch architecture

- [x] Split into independent Core, Settings Integration, Key Effects, Board Effects, Key Layout Editor, and Diagnostics patches.
- [x] Keep every patch inside one Morphe .mpp bundle.
- [x] Share runtime/settings dependencies without duplicating extension injection.
- [x] Preserve extension package names, including synthetic listeners, to prevent collisions with Gboard's obfuscated classes.

## Crash root cause and correction

- [x] Analyse the full `NoSuchMethodError` stack trace.
- [x] Confirm the extension referenced `Lc;.<init>(GboardCafeSettingsActivity, int)`.
- [x] Confirm Gboard already had its own `Lc;` with a different constructor.
- [x] Add `-dontobfuscate` and package-specific keep rules.
- [x] Check generated extension DEX for namespaced synthetic classes and absence of one-letter root class descriptors.

## Settings and effects

- [x] Dark, rounded settings UI.
- [x] Preset color palette and optional hexadecimal color.
- [x] Key-level visual effects.
- [x] Whole-board visual effects.
- [x] Automatic fade and overlay removal independent of ACTION_UP.
- [x] Explicit delayed-removal fallback and clear-active-effects action on save.
- [x] Migrate previous hidden-key preferences.

## Key layout editor

- [x] Visual drag-and-drop editor.
- [x] Move existing keys within a row or between existing rows.
- [x] Allow empty rows and arbitrary numbers of removed keys.
- [x] Hide omitted original keys so the original layout can be restored.
- [x] Recalculate LinearLayout key weights so remaining visible keys fill row space.
- [x] Add custom text keys using the active IME InputConnection.
- [x] Stable per-key IDs, including repeated labels and duplicate custom text.
- [x] Apply only when the complete primary keyboard key set matches the captured baseline.

## Release checks

- [x] Latest GitHub Actions build succeeds.
- [x] Produce a single .mpp bundle.
- [ ] Inspect latest bundle DEX for synthetic class namespace collisions.
- [ ] Apply the bundle to the supplied Gboard XAPK using Morphe.
- [ ] Install and test on the Galaxy A15.
- [ ] Verify key and board effect expiry on device.
- [ ] Verify drag/drop, removal, row fill, custom text insertion, persistence, and reset on device.
- [ ] Verify symbol and numeric layouts remain unchanged.
- [ ] Mark release as device-tested only after confirmation from the device.

## Known limitations

- Custom keys insert literal text. They do not define arbitrary native Gboard key actions or long-press alternatives.
- New keyboard rows and manual width/height sizing are not implemented.
- Equal-width/weight redistribution is applied to compatible LinearLayout row containers; other containers are left untouched.
- Runtime behavior and touch timing still require physical-device validation across supported input layouts and accessibility modes.
