package app.gboard.colemak

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private val gboardCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(
        AppTarget(version = "18.5.4.993608717-release-arm64-v8a")
    ),
)

private const val COLEMAK_SEMICOLON_KEY = "@0x7f0b239f"
private const val COLEMAK_SHIFT_SEMICOLON_KEY = "@0x7f0b0ccc"
private const val COLEMAK_LAST_VIEW = "@0x7f0b0045"

private const val ORIGINAL_COLEMAK_FRAMEWORK = "@0x7f17073d"
private const val CUSTOM_COLEMAK_FRAMEWORK = "@0x7f170000"
private const val ORIGINAL_COLEMAK_ROOT = "@0x7f17073e"
private const val CUSTOM_COLEMAK_ROOT = "@0x7f170ebc"
private const val ORIGINAL_COLEMAK_SOFTKEYS = "@0x7f170f92"
private const val CUSTOM_COLEMAK_SOFTKEYS = "@0x7f170003"
private const val ORIGINAL_COLEMAK_MAPPINGS = "@0x7f170aa9"
private const val CUSTOM_COLEMAK_MAPPINGS = "@0x7f170004"

private const val ORIGINAL_PORTRAIT_LAYOUT = "@0x7f0e02c2"
private const val COLEMAK_PORTRAIT_LAYOUT = "@0x7f0e0000"
private const val ORIGINAL_TOP_ROW_LAYOUT = "@0x7f0e02c8"
private const val COLEMAK_TOP_ROW_LAYOUT = "@0x7f0e0019"
private const val EXPANDED_KEY_WEIGHT = "0x42de38e4" // 111.1111f

private fun Element.children(): List<Element> {
    val result = mutableListOf<Element>()
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child is Element) result += child
    }
    return result
}

private fun Element.descendantsAndSelf(): List<Element> {
    val result = mutableListOf(this)
    children().forEach { result += it.descendantsAndSelf() }
    return result
}

private fun Document.replaceRootFrom(source: Document): Element {
    val imported = importNode(source.documentElement, true) as Element
    replaceChild(imported, documentElement)
    return imported
}

/**
 * Patches only the Colemak framework chain. Shared QWERTY resources remain
 * untouched, and the semicolon key remains available in the symbols keyboard.
 */
@Suppress("unused")
val removeSemicolonFromColemakPatch = resourcePatch(
    name = "Colemak: remove ; and fill the row",
    description = "Removes the Colemak semicolon and expands the nine remaining top-row keys evenly across the full row. Other layouts and the ?123 keyboard remain unchanged.",
    default = true,
) {
    compatibleWith(gboardCompatibility)

    execute {
        // q1a.xml is the Colemak-only keyboard group. Redirect its merge to a
        // private copy of p4t.xml so the generic Latin layouts are not changed.
        document("res/q1a.xml").use { colemakGroup ->
            val merges = colemakGroup.documentElement.descendantsAndSelf().filter {
                it.tagName == "merge" &&
                    it.getAttribute("def") == ORIGINAL_COLEMAK_FRAMEWORK
            }
            check(merges.size == 1) {
                "Expected one Colemak framework merge in res/q1a.xml; found ${merges.size}."
            }
            merges.single().setAttribute("def", CUSTOM_COLEMAK_FRAMEWORK)
        }

        // Clone p4t.xml and redirect only its normal-phone branch to a private
        // copy of rpH.xml. Its tablet branch is left as stock. The private root
        // uses an unreferenced XML slot; 0x7f170001 is reserved for the app's
        // APP_RESTRICTIONS metadata in the manifest.
        document("res/p4t.xml").use { stockFramework ->
            document("res/kEq.xml").use { customFramework ->
                val root = customFramework.replaceRootFrom(stockFramework)
                val matches = root.descendantsAndSelf().filter {
                    it.tagName == "include" &&
                        it.getAttribute("href") == ORIGINAL_COLEMAK_ROOT
                }
                check(matches.size == 1) {
                    "Expected one phone root include in the Colemak framework; found ${matches.size}."
                }
                matches.single().setAttribute("href", CUSTOM_COLEMAK_ROOT)
            }
        }

        // Clone the Colemak phone root and point it to private copies of its
        // key data, mapping data, and portrait layout.
        document("res/rpH.xml").use { stockRoot ->
            document("res/hyI.xml").use { customRoot ->
                val root = customRoot.replaceRootFrom(stockRoot)

                val softkeys = root.descendantsAndSelf().filter {
                    it.tagName == "softkeys" &&
                        it.getAttribute("href") == ORIGINAL_COLEMAK_SOFTKEYS
                }
                check(softkeys.size == 1) {
                    "Expected one Colemak softkey reference; found ${softkeys.size}."
                }
                softkeys.single().setAttribute("href", CUSTOM_COLEMAK_SOFTKEYS)

                val mappings = root.descendantsAndSelf().filter {
                    it.tagName == "include" &&
                        it.getAttribute("href") == ORIGINAL_COLEMAK_MAPPINGS
                }
                check(mappings.size == 1) {
                    "Expected one Colemak mapping reference; found ${mappings.size}."
                }
                mappings.single().setAttribute("href", CUSTOM_COLEMAK_MAPPINGS)

                val portraitViews = root.descendantsAndSelf().filter {
                    it.tagName == "view" &&
                        (it.getAttribute("layout") == ORIGINAL_PORTRAIT_LAYOUT ||
                            it.getAttribute("layout") == COLEMAK_PORTRAIT_LAYOUT)
                }
                check(portraitViews.size == 1) {
                    "Expected one normal portrait view in the Colemak root; found ${portraitViews.size}."
                }
                portraitViews.single().setAttribute("layout", COLEMAK_PORTRAIT_LAYOUT)
            }
        }

        // Copy the softkey definition before removing the semicolon, so no
        // language or keyboard using the shared original definition is affected.
        document("res/PWn.xml").use { stockSoftkeys ->
            document("res/R3H.xml").use { customSoftkeys ->
                val root = customSoftkeys.replaceRootFrom(stockSoftkeys)
                val matches = root.descendantsAndSelf().filter {
                    it.tagName == "softkey" &&
                        it.getAttribute("id") == COLEMAK_SEMICOLON_KEY &&
                        it.getAttribute("press_data") == ";"
                }
                check(matches.size == 1) {
                    "Expected one Colemak semicolon softkey; found ${matches.size}."
                }
                matches.single().parentNode.removeChild(matches.single())
            }
        }

        // Copy the Colemak mapping definition and remove only its normal and
        // shifted semicolon mappings for the physical last key in the row.
        document("res/VQ-.xml").use { stockMappings ->
            document("res/1Zr.xml").use { customMappings ->
                val root = customMappings.replaceRootFrom(stockMappings)
                val matches = root.descendantsAndSelf().filter {
                    it.tagName == "mapping" &&
                        it.getAttribute("view_id") == COLEMAK_LAST_VIEW &&
                        (it.getAttribute("key_id") == COLEMAK_SEMICOLON_KEY ||
                            it.getAttribute("key_id") == COLEMAK_SHIFT_SEMICOLON_KEY)
                }
                check(matches.map { it.getAttribute("key_id") }.toSet() ==
                    setOf(COLEMAK_SEMICOLON_KEY, COLEMAK_SHIFT_SEMICOLON_KEY)
                ) {
                    "Expected normal and shifted semicolon mappings in the Colemak mapping copy."
                }
                matches.forEach { it.parentNode.removeChild(it) }
            }
        }

        // Clone the stock portrait root and redirect its first row include.
        document("res/kyi.xml").use { stockLayout ->
            document("res/A19.xml").use { customLayout ->
                val root = customLayout.replaceRootFrom(stockLayout)
                val rowContainer = root.children().firstOrNull {
                    it.tagName == "LinearLayout"
                } ?: error("Could not find the keyboard row container in res/kyi.xml.")
                val rowIncludes = rowContainer.children().filter {
                    it.tagName == "include" && it.hasAttribute("layout")
                }
                check(rowIncludes.size == 2) {
                    "Expected the two row includes in res/kyi.xml; found ${rowIncludes.size}."
                }
                check(
                    rowIncludes.first().getAttribute("layout") == ORIGINAL_TOP_ROW_LAYOUT ||
                        rowIncludes.first().getAttribute("layout") == COLEMAK_TOP_ROW_LAYOUT
                ) {
                    "The first row include in res/kyi.xml was not the expected top-row layout."
                }
                rowIncludes.first().setAttribute("layout", COLEMAK_TOP_ROW_LAYOUT)
            }
        }

        // Clone the stock key rows, remove the physical semicolon slot, then
        // distribute its width evenly across the nine remaining top-row keys.
        document("res/dg2.xml").use { stockRows ->
            document("res/dO-.xml").use { customRows ->
                val root = customRows.replaceRootFrom(stockRows)
                val topRow = root.children().firstOrNull {
                    it.tagName == "LinearLayout"
                } ?: error("Could not find the top key row in res/dg2.xml.")

                val semicolonViews = topRow.children().filter {
                    it.getAttribute("id") == COLEMAK_LAST_VIEW
                }
                check(semicolonViews.size == 1) {
                    "Expected one Colemak semicolon view in the top row; found ${semicolonViews.size}."
                }
                topRow.removeChild(semicolonViews.single())

                val letterViews = topRow.children().filter {
                    it.tagName == "com.google.android.libraries.inputmethod.widgets.SoftKeyView"
                }
                check(letterViews.size == 9) {
                    "Expected nine remaining top-row keys; found ${letterViews.size}."
                }
                letterViews.forEach { it.setAttribute("layout_weight", EXPANDED_KEY_WEIGHT) }
            }
        }
    }
}
