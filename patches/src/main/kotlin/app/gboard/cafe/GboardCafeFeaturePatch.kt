package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode

private val gboardCafeCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(AppTarget(version = "18.5.4.993608717-release-arm64-v8a")),
)

private val gboardCafeSettingsEntry = resourcePatch(
    name = "Gboard Cafe: settings entry",
    description = "Adds a safe entry to open the separate Gboard Cafe settings screen.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    execute {
        // The extension contributes DEX classes, not manifest entries. Register
        // the settings Activity in the target APK itself before adding its route.
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0)
                ?: error("Gboard application element not found in AndroidManifest.xml")
            val activities = manifest.getElementsByTagName("activity")
            var activityFound = false
            for (i in 0 until activities.length) {
                val node = activities.item(i)
                val name = node.attributes?.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "name"
                )?.nodeValue
                if (name == "app.gboard.cafe.runtime.GboardCafeSettingsActivity") {
                    activityFound = true
                    break
                }
            }
            if (!activityFound) {
                val ns = "http://schemas.android.com/apk/res/android"
                val activity = manifest.createElement("activity")
                activity.setAttributeNS(
                    ns, "android:name", "app.gboard.cafe.runtime.GboardCafeSettingsActivity"
                )
                activity.setAttributeNS(ns, "android:exported", "false")
                activity.setAttributeNS(ns, "android:excludeFromRecents", "true")
                activity.setAttributeNS(
                    ns, "android:theme", "@android:style/Theme.Material.Light.NoActionBar"
                )
                application.appendChild(activity)
            }
        }

        document("res/0CM.xml").use { xml ->
            val root = xml.documentElement
            val existing = root.getElementsByTagName("Preference")
            var found = false
            for (i in 0 until existing.length) {
                val key = existing.item(i).attributes?.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "key"
                )?.nodeValue
                if (key == "gboard_cafe_settings") {
                    found = true
                    val item = existing.item(i)
                    // Replace the old fragment route: Gboard's settings screen is
                    // not a generic AndroidX PreferenceFragmentCompat host.
                    val children = item.childNodes
                    for (index in children.length - 1 downTo 0) {
                        val child = children.item(index)
                        if (child.nodeName == "intent") item.removeChild(child)
                    }
                    if (item.attributes.getNamedItemNS(
                            "http://schemas.android.com/apk/res/android", "fragment"
                        ) != null) {
                        item.attributes.removeNamedItemNS(
                            "http://schemas.android.com/apk/res/android", "fragment"
                        )
                    }
                    val intent = xml.createElement("intent")
                    intent.setAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        "android:action", "android.intent.action.MAIN"
                    )
                    intent.setAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        "android:targetPackage", "com.google.android.inputmethod.latin"
                    )
                    intent.setAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        "android:targetClass", "app.gboard.cafe.runtime.GboardCafeSettingsActivity"
                    )
                    item.appendChild(intent)
                }
            }
            if (!found) {
                val ns = "http://schemas.android.com/apk/res/android"
                val item = xml.createElement("Preference")
                item.setAttributeNS(ns, "android:key", "gboard_cafe_settings")
                item.setAttributeNS(ns, "android:title", "Gboard Cafe")
                item.setAttributeNS(ns, "android:summary", "Efeitos e organização das teclas")
                item.setAttributeNS(ns, "android:order", "900")

                val intent = xml.createElement("intent")
                intent.setAttributeNS(ns, "android:action", "android.intent.action.MAIN")
                intent.setAttributeNS(ns, "android:targetPackage", "com.google.android.inputmethod.latin")
                intent.setAttributeNS(
                    ns, "android:targetClass",
                    "app.gboard.cafe.runtime.GboardCafeSettingsActivity"
                )
                item.appendChild(intent)
                root.appendChild(item)
            }
        }
    }
}

@Suppress("unused")
val gboardCafeFeaturePatch = bytecodePatch(
    name = "Gboard Cafe: effects and layout editor",
    description = "Adds fail-safe key effects and a settings-based layout editor.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeSettingsEntry)
    extendWith("extensions/gboard-cafe/gboard-cafe.mpe")

    execute {
        val keyTouch = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;",
            name = "onTouchEvent",
            returnType = "Z",
            parameters = listOf("Landroid/view/MotionEvent;"),
        )
        val keyboardLayout = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;",
            name = "onLayout",
            returnType = "V",
            parameters = listOf("Z", "I", "I", "I", "I"),
        )

        // This hook is guarded internally and never consumes or changes the event.
        keyTouch.method.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->onTouch(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )

        // Capture-only hook at every normal exit. It does not modify the keyboard tree.
        val method = keyboardLayout.method
        val instructions = method.implementation?.instructions
            ?: error("SoftKeyboardView.onLayout has no implementation")
        val returns = instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_VOID) index else null
        }.asReversed()
        check(returns.isNotEmpty()) { "SoftKeyboardView.onLayout return point not found" }
        for (index in returns) {
            method.addInstructions(
                index,
                "invoke-static {p0}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->afterLayout(Landroid/view/View;)V",
            )
        }
    }
}
