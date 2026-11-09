# Gboard Colemak semicolon patch

Small Morphe patch for Gboard 18.5.4.993608717-release-arm64-v8a.

## What it changes

Removes the `; ` softkey from the Colemak main keyboard resource. It does not edit Gboard's separate symbols-keyboard resources, so the semicolon remains available from the `?123` screen.

The patch is intentionally restricted to the exact Gboard build above. It checks that the expected semicolon entry exists exactly once and stops safely if Google's resource layout changes.

## Build

Every push to `main` runs the GitHub Actions workflow. The resulting `.mpp` is uploaded as an Actions artifact named `gboard-colemak-patch`. A manual run is also available under Actions.

## Source

This project uses the official Morphe patches Gradle plugin and the Morphe patcher API.
