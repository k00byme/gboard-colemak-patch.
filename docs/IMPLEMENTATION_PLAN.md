# Implementation plan

Priority order: effects first, key arrangement second. Other Keys Cafe features are deferred.

## 1. Effects

- [x] Inspect the supplied Gboard and Keys Cafe APKs.
- [x] Verify the Gboard key touch, keyboard dispatch, and layout method signatures in DEX.
- [x] Identify HoneyTea key- and board-effect families.
- [x] Implement original procedural key effects: ripple, color flash, outline, bounce, spark particles, paint, blob, blob spread, footprint.
- [x] Implement original procedural keyboard effects: ring, spark, spark spin, firecracker, blob, blob spread, outline, paint gun.
- [x] Add independent effect selectors, color, duration, and master enable control to the Gboard settings entry.
- [ ] Verify drawing, release timing, long-press, repeat, and multi-touch on a physical device.

## 2. Key arrangement

- [x] Identify Gboard soft-key definition resources and key view classes.
- [x] Add Gboard Cafe to Gboard's own settings preference screen.
- [x] Capture a primary text layout only after at least three rows and twenty keys are present.
- [x] Add within-row key reordering in the settings editor.
- [x] Add hide/show and restore-original controls.
- [x] Persist and reapply saved order after keyboard view-tree reconstruction.
- [ ] Validate key labels and layout parameters on the Galaxy A15.
- [ ] Support cross-row movement, new keys, and custom press/long-press actions.
- [ ] Test languages, symbol layers, number fields, tablet/floating modes, and accessibility.

## 3. Build and release

- [x] Configure GitHub Actions to compile the Morphe patch bundle.
- [ ] Confirm the latest CI run succeeds after the effects/layout update.
- [ ] Apply the resulting .mpp bundle to the supplied Gboard APK with Morphe.
- [ ] Install and launch the patched APK.
- [ ] Verify effects and key ordering on the Samsung Galaxy A15.
- [ ] Publish a tested release only after device validation.
- [x] Restrict compatibility to the exact inspected Gboard build.

## Current status

Code for key effects, whole-keyboard effects, and persistent within-row ordering is present in the repository. CI compilation and static source checks are not substitutes for installing and testing the patched APK. No device-tested APK is being claimed at this stage.
