package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/** Captures and reapplies the user's editable key arrangement. */
val gboardCafeKeyLayoutEditorPatch = bytecodePatch(
    name = "QGFPW Layout + Black Gray Colors",
    description = "Applies Qfpxzglduh / Arsyjneio / Tcwvbkm, gray glyphs #3B3B3B and solid-black keyboard surface with theme-controlled special keys.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    dependsOn(gboardCafeSettingsIntegrationPatch)
    dependsOn(gboardCafeNativeSpecialKeyColorsPatch)

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

        // No keyboard dispatch/touch hook is installed here. The direct post-layout
        // callback is sufficient and keeps the typing event path untouched.
    }
}
