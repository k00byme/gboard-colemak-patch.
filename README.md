# Gboard Colemak row-fill patch

Morphe patch for Gboard 18.5.4.993608717-release-arm64-v8a.

## What it changes

- Removes the semicolon key from the Colemak main keyboard.
- Removes the now-unused semicolon/shifted-colon view mapping.
- Replaces Colemak's portrait keyboard body with a private copy of the stock layout.
- Removes the semicolon's physical slot from the Colemak top row and increases the nine remaining letter-key weights evenly, so they fill the same total row width.
- Leaves QWERTY and other layouts at their original widths.
- Does not edit the `?123` symbols resources; semicolon remains available there.

The patch uses existing, otherwise unreferenced layout resource IDs as private copies, avoiding changes to the shared QWERTY layout resources.

## Build

Every push to `main` runs the GitHub Actions workflow. The resulting `.mpp` is uploaded as an Actions artifact named `gboard-colemak-patch`. A manual run is also available under Actions.

## Compatibility

Only Gboard `18.5.4.993608717-release-arm64-v8a` is targeted. The patch checks the expected XML entries and refuses to continue if the resource structure differs.
