package app.gboard.cafe

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val gboardCafeCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(AppTarget(version = "18.5.4.993608717-release-arm64-v8a")),
)
