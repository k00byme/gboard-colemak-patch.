package app.gboard.cafe

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch

/**
 * Analysis-only patch for the exact Gboard build supplied with this project.
 *
 * It validates the real keyboard-definition resources and reports the soft-key
 * schema. It deliberately does not alter keyboard behavior.
 */
@Suppress("unused")
val gboardCafeDiscoveryPatch = resourcePatch(
    name = "Gboard Cafe: Gboard structure discovery",
    description = "Validates the target Gboard keyboard-definition resources.",
    default = false,
) {
    compatibleWith(gboardCafeCompatibility)

    execute {
        val entries = listApkEntries("res/")
        val requiredResources = listOf(
            "res/PWn.xml",
            "res/VvI.xml",
            "res/iXt.xml",
        )

        val missing = requiredResources.filterNot(entries::contains)
        check(missing.isEmpty()) {
            "Gboard keyboard resources changed; missing: ${missing.joinToString()}"
        }

        document("res/PWn.xml").use { xml ->
            val softkeys = xml.getElementsByTagName("softkey")
            val softkeyLists = xml.getElementsByTagName("softkey_list")
            check(softkeys.length > 0) {
                "res/PWn.xml contains no softkey elements; refusing to continue."
            }
            check(softkeyLists.length > 0) {
                "res/PWn.xml contains no softkey_list elements; refusing to continue."
            }

            println("[Gboard Cafe] PWn.xml softkey elements: ${softkeys.length}")
            println("[Gboard Cafe] PWn.xml softkey_list elements: ${softkeyLists.length}")
        }

        document("res/VvI.xml").use { xml ->
            val softkeys = xml.getElementsByTagName("softkey")
            check(softkeys.length > 0) {
                "res/VvI.xml contains no softkey elements; refusing to continue."
            }
            println("[Gboard Cafe] VvI.xml softkey elements: ${softkeys.length}")
        }

        println("[Gboard Cafe] Required keyboard resources are present.")
        println("[Gboard Cafe] Discovery only: no keyboard behavior was changed.")
    }
}
