package org.k00byme.samsungkeyboard

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val SAMSUNG_KEYBOARD_595010300 = Compatibility(
    name = "Samsung Keyboard",
    packageName = "com.samsung.android.honeyboard",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x3E91FF,
    targets = listOf(
        AppTarget(version = "5.9.50.10"),
    ),
)
