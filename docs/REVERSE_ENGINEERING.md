# Reverse engineering notes

## Input builds

- Gboard XAPK: `gboard-18-5-4-993608717-release-arm64-v8a.xapk`
- Keys Cafe APK: `samsung-keys-cafe-1-8-00-13.apk`

The uploaded APKs are analysis inputs only and are not committed to this repository.

## Gboard areas identified during the initial inspection

The initial inspection identified candidate components/names that need to be revalidated against the exact DEX and resource tables before writing hooks:

- `SoftKeyView`: candidate key-view rendering surface.
- `KeyboardView`: candidate keyboard container/view.
- `KeyboardDef`: candidate layout definition.
- `ThemeBuilderActivity` and `ThemeEditorActivity`: candidate theme configuration surfaces.

These names are reconnaissance notes, not stable public APIs. Before any patch uses them, record the defining DEX descriptor, methods, field types, call sites, and a reproducible fingerprint.

## Keys Cafe areas identified during the initial inspection

The APK contains feature areas/classes associated with:

- HoneyTea: visual effects, colors, sounds and animations.
- Latte: keyboard layout editing and key dimensions.
- BubbleTea: themes.
- OreoShake: gesture customization.
- Statistics and Roaster: secondary features.

Effect-related identifiers observed in the initial inspection include:

- `KeySpark`
- `KeyBlob`
- `KeyBlobSpread`
- `KeyColorFlash`
- `KeyFootPrint`
- `KeyOutline`
- `BoardFireCracker`
- `BoardPaintGun`
- `BoardRingSpread`
- `BoardSparkSpin`
- `KeyBounce`

These identifiers are a discovery list only. Their original implementations must not be copied blindly: determine their inputs, drawing lifecycle, asset dependencies, and Samsung framework calls first.

## Platform coupling

The first manifest inspection found Samsung-specific identity/integration requirements, including the `android.uid.honeyboard` shared UID and Honeyboard-facing services/permissions. That is a strong sign that direct APK-to-APK class transplantation is not a viable general strategy.

The implementation should reproduce behavior on Gboard's own rendering and settings architecture. Do not add Samsung shared-UID declarations or depend on Samsung-private services.

## Required next inspection

1. Extract all XAPK splits and record package/version/ABI for each.
2. Decompile the exact Gboard and Keys Cafe builds.
3. For each prioritized Keys Cafe effect, trace constructor/configuration, drawing/update methods, resources and service dependencies.
4. Trace Gboard key touch, press/release, invalidation and draw paths.
5. Trace Gboard settings navigation and preference fragment registration.
6. Build a mapping table from each Keys Cafe effect to a Gboard hook or a documented reason it must be recreated.
