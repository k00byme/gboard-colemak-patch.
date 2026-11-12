package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/** Key-level touch effects; can be disabled without disabling board/layout patches. */
val gboardCafeKeyEffectsPatch = bytecodePatch(
    name = "Key Effects",
    description = "Adds visual effects to the individual key being pressed.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeSettingsIntegrationPatch)

    execute {
        val keyTouch = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;",
            name = "onTouchEvent",
            returnType = "Z",
            parameters = listOf("Landroid/view/MotionEvent;"),
        )
        keyTouch.method.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->onTouch(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )
    }
}
