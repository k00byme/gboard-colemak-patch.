# Implementation plan

Priority order follows the project brief: key effects first, then editable key arrangement.

## 1. Visual key effects

- [x] Inspect the supplied Gboard and Keys Cafe APKs.
- [x] Identify Gboard's key view and key-definition classes.
- [x] Identify the relevant HoneyTea effect abstractions.
- [ ] Fingerprint the exact Gboard touch lifecycle and key-view drawing methods.
- [ ] Add an original effect renderer to Gboard's key view lifecycle.
- [ ] Implement configurable effects with enable/disable, intensity, duration, and color.
- [ ] Add preview and settings controls.
- [ ] Verify press, release, long-press, repeat, and multi-touch behavior.

Initial effect set to recreate: color flash, outline pulse, ripple, bounce, spark particles, and paint/blob-style spread. These are original implementations, not copied Samsung code.

## 2. Key arrangement editor

- [x] Identify the Gboard soft-key XML schema and keyboard variant metadata.
- [x] Identify `SoftKeyView`, `SoftKeyViewsMapper`, and `KeyboardDefManager`.
- [ ] Fingerprint the exact keyboard-definition load path and all callers.
- [ ] Define a serializable layout model with stable key identifiers, row membership, order, labels, and press/long-press actions.
- [ ] Add a drag-and-drop editor with add, remove, move, and reset actions.
- [ ] Persist user changes and apply them when Gboard loads the selected layout.
- [ ] Preserve locale, symbol layers, long-press alternatives, split/tablet variants, and accessibility behavior.
- [ ] Add a settings entry point and an in-keyboard preview.

## 3. Release requirements

- [ ] CI build succeeds and produces a Morphe `.mpp` bundle.
- [ ] Patch applies to the supplied Gboard version.
- [ ] Patched APK installs and launches.
- [ ] Effects work on the user's Samsung Galaxy A15 without root.
- [ ] Layout editor saves and restores custom layouts after Gboard restarts.
- [ ] No changes are applied to unsupported Gboard versions.

## Current state

The APKs have now been inspected directly. The project has reliable class/resource targets, but exact DEX method prototypes and safe bytecode insertion points are still unverified. Therefore the effects and editor are not yet implemented. The discovery patch must not be presented as a working feature build.
