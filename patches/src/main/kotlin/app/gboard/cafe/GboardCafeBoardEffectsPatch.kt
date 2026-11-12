package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/** Whole-keyboard effects; independent from the per-key touch renderer. */
val gboardCafeBoardEffectsPatch = bytecodePatch(
    name = "Board Effects",
    description = "Adds visual effects over the complete keyboard surface.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeSettingsIntegrationPatch)

    execute {
        val keyboardTouch = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;",
            name = "dispatchTouchEvent",
            returnType = "Z",
            parameters = listOf("Landroid/view/MotionEvent;"),
        )
        keyboardTouch.method.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->onBoardTouch(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )
    }
}
