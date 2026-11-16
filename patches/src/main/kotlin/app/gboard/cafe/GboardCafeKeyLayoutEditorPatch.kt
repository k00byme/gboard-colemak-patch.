package app.gboard.cafe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/** Captures and reapplies the user's editable key arrangement. */
val gboardCafeKeyLayoutEditorPatch = bytecodePatch(
    name = "QGFPW Layout + Black Gray Colors",
    description = "Applies Qgfpwjlduh / Arszyneio / Tcxvbkm, gray glyphs #3B3B3B and black key bubbles #000000.",
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

        // Gboard paints some special key bubbles directly on Canvas, outside
        // View backgrounds/Drawables. Paint them after the native key onDraw,
        // before Android dispatches the child glyphs and labels.
        val keyDrawing = Fingerprint(
            definingClass = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;",
            name = "onDraw",
            returnType = "V",
            parameters = listOf("Landroid/graphics/Canvas;"),
        )
        val drawMethod = keyDrawing.method
        val drawInstructions = drawMethod.implementation?.instructions
            ?: error("SoftKeyView.onDraw has no implementation")
        val drawReturns = drawInstructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_VOID) index else null
        }.asReversed()
        check(drawReturns.isNotEmpty()) { "SoftKeyView.onDraw return point not found" }
        for (index in drawReturns) {
            drawMethod.addInstructions(
                index,
                "invoke-static/range {p0 .. p1}, Lapp/gboard/cafe/runtime/GboardCafeRuntime;->drawBlackKeyBubble(Landroid/view/View;Landroid/graphics/Canvas;)V",
            )
        }

        // No keyboard dispatch/touch hook is installed here. The direct post-layout
        // callback is sufficient and keeps the typing event path untouched.
    }
}
