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
    description = "Adds Gboard Cafe to the existing Gboard preferences screen.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    execute {
        document("res/0CM.xml").use { xml ->
            val root = xml.documentElement
            val existing = root.getElementsByTagName("Preference")
            var found = false
            for (i in 0 until existing.length) {
                val key = existing.item(i).attributes?.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "key"
                )?.nodeValue
                if (key == "gboard_cafe_settings") found = true
            }
            if (!found) {
                val ns = "http://schemas.android.com/apk/res/android"
                val item = xml.createElement("Preference")
                item.setAttributeNS(ns, "android:key", "gboard_cafe_settings")
                item.setAttributeNS(ns, "android:title", "Gboard Cafe")
                item.setAttributeNS(ns, "android:summary", "Efeitos e organização das teclas")
                item.setAttributeNS(ns, "android:fragment", "app.gboard.cafe.runtime.GboardCafeSettingsFragment")
                item.setAttributeNS(ns, "android:order", "900")
                root.appendChild(item)
            }
        }
    }
}

@Suppress("unused")
val gboardCafeFeaturePatch = bytecodePatch(
    name = "Gboard Cafe: effects and layout editor",
    description = "Adds key animations and a native preferences editor to Gboard.",
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

        keyTouch.method.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->onTouch(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )

        val method = keyboardLayout.method
        val instructions = method.implementation?.instructions
            ?: error("SoftKeyboardView.onLayout has no implementation")
        val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        check(returnIndex >= 0) { "SoftKeyboardView.onLayout return point not found" }
        method.addInstructions(
            returnIndex,
            "invoke-static {p0}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->afterLayout(Landroid/view/View;)V",
        )
    }
}
