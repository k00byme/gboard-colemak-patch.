package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/** Captures and reapplies the user's editable key arrangement. */
val gboardCafeKeyLayoutEditorPatch = bytecodePatch(
    name = "Key Layout Editor",
    description = "Enables persistent reordering and visibility of existing keyboard keys.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeSettingsIntegrationPatch)

    execute {
        val keyboardLayout = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;",
            name = "onLayout",
            returnType = "V",
            parameters = listOf("Z", "I", "I", "I", "I"),
        )
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

        // Fallback lifecycle hook: this method is already used by the working
        // Board Effects patch, so it also catches a keyboard reused without layout.
        val keyboardTouch = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;",
            name = "dispatchTouchEvent",
            returnType = "Z",
            parameters = listOf("Landroid/view/MotionEvent;"),
        )
        keyboardTouch.method.addInstructions(
            0,
            "invoke-static {p0, p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->onKeyboardTouch(Landroid/view/View;Landroid/view/MotionEvent;)V",
        )
    }
}
