package org.k00byme.samsungkeyboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Intercepts committed text in Samsung's InputConnection wrapper.
 * Only the extension runtime performs accent-only changes; ambiguous forms are left untouched.
 */
@Suppress("unused")
val portugueseAccentOnlyPatch = bytecodePatch(
    name = "Portuguese accent-only correction",
    description = "Adds known, unambiguous Portuguese accents without general spell correction.",
    default = false,
) {
    compatibleWith(SAMSUNG_KEYBOARD_595010300)
    extendWith("extensions/samsung-keyboard/samsung-keyboard.mpe")

    execute {
        val commitText = Fingerprint(
            definingClass = "Lmd/b;",
            name = "commitText",
            returnType = "Z",
            parameters = listOf("Ljava/lang/CharSequence;", "I"),
        )

        // In Samsung Keyboard 5.9.50.10, commitText has p0=this, p1=text.
        // The runtime returns the original CharSequence unless a safe correction applies.
        commitText.method.addInstructions(
            0,
            """
                invoke-static {p0, p1}, Lorg/k00byme/samsungkeyboard/runtime/AccentOnlyRuntime;->normalizeCommit(Ljava/lang/Object;Ljava/lang/CharSequence;)Ljava/lang/CharSequence;
                move-result-object p1
            """.trimIndent(),
        )
    }
}
