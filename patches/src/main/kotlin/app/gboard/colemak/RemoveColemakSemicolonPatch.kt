package app.gboard.colemak

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val gboardCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(
        AppTarget(version = "18.5.4.993608717-release-arm64-v8a")
    ),
)

/**
 * Removes the semicolon softkey from the Colemak main keyboard resource.
 *
 * The target resource is used by the Colemak keyboard group. Gboard's separate
 * symbols-keyboard resources are not edited, so the semicolon remains available
 * from the ?123 screen.
 */
@Suppress("unused")
val removeSemicolonFromColemakPatch = resourcePatch(
    name = "Remove ; from Colemak",
    description = "Removes the semicolon key from Gboard's Colemak main keyboard. The ?123 symbols keyboard is unchanged.",
    default = true,
) {
    compatibleWith(gboardCompatibility)

    execute {
        document("res/PWn.xml").use { xml ->
            val softkeys = xml.getElementsByTagName("softkey")
            val matches = mutableListOf<Element>()

            for (index in 0 until softkeys.length) {
                val element = softkeys.item(index) as? Element ?: continue
                if (
                    element.getAttribute("press_data") == ";" &&
                    element.getAttribute("long_press_data") == ":"
                ) {
                    matches += element
                }
            }

            check(matches.size == 1) {
                "Expected exactly one Colemak semicolon softkey in res/PWn.xml; found ${matches.size}. " +
                    "The Gboard resource layout may have changed; refusing to patch."
            }

            val semicolonKey = matches.single()
            val parent = semicolonKey.parentNode
            check(parent != null && parent.nodeName == "softkey_list") {
                "The semicolon softkey is not inside the expected softkey_list."
            }

            parent.removeChild(semicolonKey)
        }
    }
}
