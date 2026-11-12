# Implementation plan

## Patch architecture

- [x] Split the old monolithic patch into independent Core, Settings Integration, Key Effects, Board Effects, Key Layout Editor, and Diagnostics patches.
- [x] Keep all patch definitions in one .mpp output.
- [x] Make feature patches depend on the shared Core/settings integration rather than injecting the same extension repeatedly.
- [x] Preserve the runtime namespace and prevent R8 obfuscation of synthetic listener classes.

## Crash root cause and fix

- [x] Read the full NoSuchMethodError stack trace.
- [x] Inspect extension DEX: the Activity's save-button listener invoked `Lc;.<init>(GboardCafeSettingsActivity, int)`.
- [x] Inspect the original Gboard DEX: `Lc;` already exists in `classes2.dex` and only exposes a different constructor `(Lay;)`.
- [x] Add `-dontobfuscate` and package keep rules so extension synthetic classes cannot collide with host obfuscated classes.
- [ ] Inspect new .mpp DEX and prove all synthetic listener descriptors remain namespaced.

## Features

- [x] Key effects runtime and UI.
- [x] Whole-board effects runtime and UI.
- [x] Persistent within-row key ordering and hide/show/reset UI.
- [x] Gboard settings entry for the main Cafe screen.
- [x] Optional diagnostics page.
- [ ] Extend the editor to cross-row movement/new keys only after DEX/layout-definition behavior is validated.

## Release validation

- [ ] Latest CI bundle compiles.
- [ ] Inspect generated extension DEX for collisions.
- [ ] Apply the modular patches to the supplied Gboard XAPK using Morphe.
- [ ] Install and test on a real device.
- [ ] Verify the Settings Integration screen, key effects, board effects, and layout persistence.
- [ ] Mark as device-tested only after the user's device confirms runtime behavior.

## Known limitations

- Layout editing is limited to reordering within existing rows and hiding/showing existing keys.
- Board/key effect timing and multitouch need device testing.
- Gboard updates, locales, symbol pages, number pad, floating/tablet mode, and accessibility need separate validation.
