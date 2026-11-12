# Reverse engineering notes

Target APKs supplied for this project:

- Gboard XAPK: `18.5.4.993608717-release-arm64-v8a`
- Samsung Keys Cafe APK: `1.8.00.13`

## Gboard findings

The XAPK contains the base Gboard APK plus six configuration/feature splits. The base APK contains four DEX files and obfuscated resource XML names.

Confirmed resource and code surfaces in the supplied build:

- `res/PWn.xml` contains Gboard's soft-key layout schema: `softkeys`, `softkey_list`, `softkey`, `press_data`, `long_press_data`, `template_id`.
- `res/VvI.xml` also contains the soft-key list schema and is a concrete keyboard-definition resource.
- `res/iXt.xml` contains keyboard variant metadata, including `keyboard_group`, `variant`, `variant_label`, and `force_show_number_row`.
- DEX class names include:
  - `com.google.android.libraries.inputmethod.widgets.SoftKeyView`
  - `com.google.android.libraries.inputmethod.widgets.SoftKeyboardView`
  - `com.google.android.libraries.inputmethod.keyboard.impl.SoftKeyViewsMapper`
  - `com.google.android.libraries.inputmethod.metadata.KeyboardDefManager`
  - `com.google.android.libraries.inputmethod.metadata.KeyboardDef`
  - `com.google.android.libraries.inputmethod.metadata.KeyboardViewDef`
  - `com.google.android.apps.inputmethod.libs.theme.builder.ThemeBuilderActivity`
  - `com.google.android.apps.inputmethod.libs.theme.builder.ThemeEditorActivity`
- DEX strings confirm `SoftKeyView.setSoftKeyDef`, `SoftKeyView.onMeasure`, `SoftKeyViewsMapper.initSoftKeyViews`, `SoftKeyViewsMapper.updateSoftKeyViews`, and `KeyboardDefManager.loadKeyboardDefFromXml`.

These findings establish that Gboard has a structured key-definition layer and a separate view-mapping layer. They do not yet establish exact method prototypes or safe injection points. Those must be fingerprinted against the supplied DEX before adding bytecode hooks.

## Keys Cafe findings

The supplied Keys Cafe DEX contains Samsung-specific HoneyTea classes and abstractions, including:

- `HoneyTeaKeyViewModel`, `HoneyTeaKeyboardViewModel`, and `HoneyTeaTouchInfo`
- `IEffector`
- `EffectAlpha`, `BlobSpreadAlpha`, and `PaintGunAlpha`
- `HoneyTeaKeyView` and `HoneyTeaKeyboardViewHolder`

This supports the decision to recreate the visual behavior rather than transplant the Samsung classes. Keys Cafe is coupled to Honeyboard plugin interfaces and Samsung package namespaces; its implementation is not a drop-in Gboard library.

## Implementation boundary

1. Implement visual effects as original Gboard-side drawing/animation code, driven by key touch state. Do not copy Samsung bytecode or assets.
2. Implement layout editing by storing a user layout model and applying it to Gboard's key-definition pipeline. Preserve the original layout as the reset/fallback state.
3. Add the editor entry point only after the target settings/activity flow is fingerprinted. Do not guess an Activity or preference resource.
4. Every bytecode hook must have a version-specific fingerprint and a strict match-count assertion. A patch must fail closed if the target changes.
5. The current discovery patch is an analysis patch, not a user-facing feature patch.
