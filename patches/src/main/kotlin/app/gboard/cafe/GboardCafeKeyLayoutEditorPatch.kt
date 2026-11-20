package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Captures and reapplies the user's editable key arrangement. */
val gboardCafeKeyLayoutEditorPatch = bytecodePatch(
    name = "QGFPW Layout + Black Gray Colors",
    description = "Applies Qgfpwjlduh / Arszyneio / Tcxvbkm, gray glyphs #3B3B3B and native black special-key backgrounds #000000.",
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

        // Gboard 18.5.4 merges its built-in and selected theme stylesheets in
        // StyleSheetProtoUtils.i([Lrrv;). Append the black surface overlay to
        // that merge builder before it is frozen into the final stylesheet.
        val themeMerge = Fingerprint(
            definingClass = "Lrqm;",
            name = "i",
            returnType = "Lrrv;",
            parameters = listOf("[Lrrv;"),
        )
        val mergeMethod = themeMerge.method
        val mergeInstructions = mergeMethod.implementation?.instructions?.toList()
            ?: error("StyleSheetProtoUtils.i has no implementation")
        val builderFinalizeIndex = mergeInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                reference?.definingClass == "Laali;" &&
                reference.name == "o" &&
                reference.parameterTypes.isEmpty()
        }
        check(builderFinalizeIndex >= 0) {
            "StyleSheetProtoUtils.i builder finalize call was not found"
        }
        mergeMethod.addInstructions(
            builderFinalizeIndex,
            "invoke-static {v0}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->forceBlackKeyboardBackground(Ljava/lang/Object;)V",
        )

        // No keyboard dispatch/touch hook is installed here. The direct post-layout
        // callback is sufficient and keeps the typing event path untouched.
    }
}
