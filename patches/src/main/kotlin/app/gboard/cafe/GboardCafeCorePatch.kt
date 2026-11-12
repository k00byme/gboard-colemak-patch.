package app.gboard.cafe

import app.morphe.patcher.patch.bytecodePatch

/**
 * Shared runtime code. Other feature patches depend on this patch so the extension
 * DEX is inserted exactly once, while their hooks remain independently selectable.
 */
val gboardCafeCorePatch = bytecodePatch(
    name = "Gboard Cafe Core",
    description = "Adds the namespaced Cafe runtime and settings Activities.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)
    extendWith("extensions/gboard-cafe/gboard-cafe.mpe")
}
