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
private const val ORIGINAL_PORTRAIT_LAYOUT = "@0x7f0e02c2"
private const val COLEMAK_PORTRAIT_LAYOUT = "@0x7f0e0000"
private const val COLEMAK_TOP_ROW_LAYOUT = "@0x7f0e0026"
private const val EXPANDED_KEY_WEIGHT = "0x42de38e4" // 111.1111f

private fun Element.children(): List<Element> {
    val result = mutableListOf<Element>()
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child is Element) result += child
    }
    return result
}

private fun Document.replaceRootFrom(source: Document): Element {
    val imported = importNode(source.documentElement, true) as Element
    replaceChild(imported, documentElement)
    return imported
}

/**
 * Removes Colemak's semicolon key and reflows the 9 remaining top-row letters
 * to occupy the full original row width. QWERTY and other layouts retain their
 * original 10-key geometry. The ?123 symbols resources are not changed.
 */
@Suppress("unused")
val removeSemicolonFromColemakPatch = resourcePatch(
    name = "Colemak: remove ; and fill the row",
    description = "Removes the Colemak semicolon and expands the nine remaining top-row keys evenly across the full row. The ?123 symbols keyboard is unchanged.",
    default = true,
) {
    compatibleWith(gboardCompatibility)

    execute {
        // Remove the semicolon softkey data from Colemak's punctuation resource.
        document("res/PWn.xml").use { xml ->
            val matches = mutableListOf<Element>()
            val softkeys = xml.getElementsByTagName("softkey")
            for (i in 0 until softkeys.length) {
                val key = softkeys.item(i) as? Element ?: continue
                if (
                    key.getAttribute("id") == COLEMAK_SEMICOLON_KEY &&
                    key.getAttribute("press_data") == ";"
                ) matches += key
            }
            check(matches.size == 1) {
                "Expected exactly one Colemak semicolon softkey in res/PWn.xml; found ${matches.size}."
            }
            matches.single().parentNode.removeChild(matches.single())
        }

        // Remove normal and shifted mappings for the now-absent semicolon key.
        // These are separate mapping resources used by Colemak variants.
        for (path in listOf("res/VQ-.xml", "res/hYu.xml")) {
            document(path).use { xml ->
                val mappings = xml.getElementsByTagName("mapping")
                val matches = mutableListOf<Element>()
                for (i in 0 until mappings.length) {
                    val mapping = mappings.item(i) as? Element ?: continue
                    val keyId = mapping.getAttribute("key_id")
                    val viewId = mapping.getAttribute("view_id")
                    if (
                        viewId == COLEMAK_LAST_VIEW &&
                        (keyId == COLEMAK_SEMICOLON_KEY || keyId == COLEMAK_SHIFT_SEMICOLON_KEY)
                    ) matches += mapping
                }
                check(matches.map { it.getAttribute("key_id") }.toSet() ==
                    setOf(COLEMAK_SEMICOLON_KEY, COLEMAK_SHIFT_SEMICOLON_KEY)
                ) {
                    "Expected Colemak semicolon and shifted-colon mappings in $path."
                }
                matches.forEach { it.parentNode.removeChild(it) }
            }
        }

        // Clone the stock portrait keyboard layout into two existing, unused
        // layout resource IDs. This keeps the geometry change exclusive to
        // Colemak instead of widening QWERTY and every other keyboard.
        document("res/kyi.xml").use { stockRoot ->
            document("res/A19.xml").use { customRoot ->
                val root = customRoot.replaceRootFrom(stockRoot)
                val rowInclude = root.getElementsByTagName("include")
                var replaced = false
                for (i in 0 until rowInclude.length) {
                    val include = rowInclude.item(i) as? Element ?: continue
                    if (include.getAttribute("layout") == "@0x7f0e02c8") {
                        include.setAttribute("layout", COLEMAK_TOP_ROW_LAYOUT)
                        replaced = true
                        break
                    }
                }
                check(replaced) { "Could not locate the stock top-row layout include in res/kyi.xml." }
            }
        }

        // Clone the stock key rows, remove the physical semicolon slot, and
        // distribute its width evenly over the nine remaining top-row keys.
        document("res/dg2.xml").use { stockRows ->
            document("res/zzH.xml").use { customRows ->
                val root = customRows.replaceRootFrom(stockRows)
                val rowLayouts = root.getElementsByTagName("LinearLayout")
                check(rowLayouts.length >= 2) { "Unexpected keyboard row layout in res/dg2.xml." }
                val topRow = rowLayouts.item(1) as Element
                val semicolonViews = topRow.children().filter {
                    it.getAttribute("id") == COLEMAK_LAST_VIEW
                }
                check(semicolonViews.size == 1) {
                    "Expected exactly one Colemak semicolon view in the top row; found ${semicolonViews.size}."
                }
                topRow.removeChild(semicolonViews.single())

                val letterViews = topRow.children().filter {
                    it.tagName == "com.google.android.libraries.inputmethod.widgets.SoftKeyView"
                }
                check(letterViews.size == 9) {
                    "Expected nine remaining top-row keys after removing semicolon; found ${letterViews.size}."
                }
                letterViews.forEach { it.setAttribute("layout_weight", EXPANDED_KEY_WEIGHT) }
            }
        }

        // Select the custom 9-key portrait layout only for this Colemak keyboard.
        document("res/rpH.xml").use { xml ->
            val views = xml.getElementsByTagName("view")
            val matches = mutableListOf<Element>()
            for (i in 0 until views.length) {
                val view = views.item(i) as? Element ?: continue
                if (view.getAttribute("layout") == ORIGINAL_PORTRAIT_LAYOUT) matches += view
            }
            check(matches.size == 1) {
                "Expected exactly one normal portrait keyboard view in res/rpH.xml; found ${matches.size}."
            }
            matches.single().setAttribute("layout", COLEMAK_PORTRAIT_LAYOUT)
        }
    }
}
