# Reverse engineering notes

Target APKs supplied for this project:
- Gboard XAPK: `18.5.4.993608717-release-arm64-v8a`
- Samsung Keys Cafe APK: `1.8.00.13`

## Confirmed Gboard surfaces

The XAPK contains the Gboard base APK plus configuration/feature splits. The base APK has four DEX files and obfuscated resource XML names.

Confirmed resources:
- `res/PWn.xml`: soft-key definition schema (`softkeys`, `softkey_list`, `softkey`, `press_data`, `long_press_data`, `template_id`).
- `res/VvI.xml`: another concrete soft-key definition resource.
- `res/iXt.xml`: keyboard variant metadata.
- `res/0CM.xml`: Gboard settings preference screen, where the Gboard Cafe entry is inserted.

Confirmed DEX class/method signatures in the supplied build:
- `SoftKeyView.onTouchEvent(android.view.MotionEvent): boolean`
- `SoftKeyboardView.dispatchTouchEvent(android.view.MotionEvent): boolean`
- `SoftKeyboardView.onLayout(boolean, int, int, int, int): void`
- `KeyboardDef`, `KeyboardViewDef`, and `SoftKeyDef`

The hooks match the exact Gboard package, version, class, method name, return type, and parameters. Touch helpers are fail-safe and do not consume or replace the original Gboard event. The layout observer runs after Gboard's original `onLayout` returns.

Strings resembling `SoftKeyViewsMapper` and `KeyboardDefManager` occur in DEX strings but are not treated as verified class definitions or hook targets.

## Keys Cafe findings

The supplied Keys Cafe DEX contains Samsung-specific HoneyTea abstractions:
- Interfaces/base: `IEffector`, `ColorEffector`, `EffectAlpha`
- Key effects: `KeyBlobEffector`, `KeyBlobSpreadEffector`, `KeyColorFlashEffector`, `KeyFootPrintEffector`, `KeyOutlineEffector`, `KeySparkEffector`
- Key motion: `KeyBounceEffecter`, `KeyBounceTouchEffecter`, `KeyRotateEffecter`, `KeySpringEffecter`, `KeyTiltEffector`
- Board effects: `BoardBlobEffector`, `BoardBlobSpreadEffector`, `BoardFireCrackerEffector`, `BoardOutlineEffector`, `BoardPaintGunEffector`, `BoardRingSpreadEffector`, `BoardSparkEffector`, `BoardSparkSpinEffector`

They depend on Samsung Honeyboard plugin interfaces and are not drop-in Gboard components. Gboard Cafe recreates selected behavior using original procedural drawing code and does not include Samsung bytecode or assets.

## Current implementation

- Key-level effects render on the touched Gboard key overlay.
- Whole-keyboard effects render on the SoftKeyboardView overlay.
- Gboard's own settings screen links to a settings Activity inside the Gboard package.
- The editor stores the captured key order, reorders existing keys within a row, and can hide/show keys.
- Saved order/visibility is reapplied after Gboard rebuilds the keyboard view tree when the current key-row structure still matches the saved layout.
- Initial capture requires at least three rows and twenty keys to avoid PIN/numeric layouts.

## Not yet validated on a device

The project compiles in CI, but has not yet been patched, installed, or exercised on the Galaxy A15. In particular:
- Exact visual behavior, animation timing, and touch/multitouch interaction need physical-device tests.
- Reordering is limited to keys within an existing row; new keys and cross-row movement are not implemented.
- Locale variants, symbol pages, number pads, tablet/floating layouts, and accessibility require separate validation.
- Only the supplied Gboard version is targeted. Updates require fresh DEX/resource inspection.
