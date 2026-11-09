package app.gboard.colemak

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

private val gboardCompatibility = Compatibility(
    name = "Gboard",
    packageName = "com.google.android.inputmethod.latin",
    targets = listOf(
        AppTarget(version = "18.5.4.993608717-release-arm64-v8a")
    ),
)

private const val DEFAULT_LAYOUT = "qwertzuiopasdfghjklyxcvbnm"

// The 26 key IDs in Gboard's stock QWERTZ mapping, in DEFAULT_LAYOUT order.
// The uppercase IDs are used by the SHIFT,CAPS mapping group.
private val lowercaseKeyIds = listOf(
    "@0x7f0b198e", "@0x7f0b19fe", "@0x7f0b1905", "@0x7f0b1992",
    "@0x7f0b19c5", "@0x7f0b1a0d", "@0x7f0b19e6", "@0x7f0b1934",
    "@0x7f0b1971", "@0x7f0b1987", "@0x7f0b18d4", "@0x7f0b199d",
    "@0x7f0b18f7", "@0x7f0b191b", "@0x7f0b191f", "@0x7f0b192e",
    "@0x7f0b1945", "@0x7f0b1949", "@0x7f0b1951", "@0x7f0b1a06",
    "@0x7f0b1a03", "@0x7f0b18e9", "@0x7f0b19fb", "@0x7f0b18e4",
    "@0x7f0b1965", "@0x7f0b195e",
)

private val uppercaseKeyIds = listOf(
    "@0x7f0b1877", "@0x7f0b18a5", "@0x7f0b1813", "@0x7f0b1878",
    "@0x7f0b1886", "@0x7f0b18ad", "@0x7f0b1890", "@0x7f0b1838",
    "@0x7f0b1860", "@0x7f0b1875", "@0x7f0b17f7", "@0x7f0b187d",
    "@0x7f0b180f", "@0x7f0b1826", "@0x7f0b1828", "@0x7f0b1833",
    "@0x7f0b1849", "@0x7f0b184b", "@0x7f0b184f", "@0x7f0b18a9",
    "@0x7f0b18a8", "@0x7f0b1808", "@0x7f0b18a3", "@0x7f0b1806",
    "@0x7f0b1857", "@0x7f0b1853",
)

private val topRowViews = listOf(
    "@0x7f0b0039", "@0x7f0b003a", "@0x7f0b003b", "@0x7f0b003c",
    "@0x7f0b003e", "@0x7f0b0040", "@0x7f0b0042", "@0x7f0b0043",
    "@0x7f0b0044", "@0x7f0b0045",
)

private val middleRowViews = listOf(
    "@0x7f0b0024", "@0x7f0b0025", "@0x7f0b0026", "@0x7f0b0027",
    "@0x7f0b0029", "@0x7f0b002b", "@0x7f0b002d", "@0x7f0b002e",
    "@0x7f0b002f",
)

private val bottomRowViews = listOf(
    "@0x7f0b000e", "@0x7f0b000f", "@0x7f0b0011", "@0x7f0b0013",
    "@0x7f0b0015", "@0x7f0b0017", "@0x7f0b0018",
)

// The split layout mirrors the centre letter on both sides of its gap.
private const val SPLIT_HOME_DUPLICATE_VIEW = "@0x7f0b002a"
private const val SPLIT_BOTTOM_DUPLICATE_VIEW = "@0x7f0b0014"

private fun Element.children(): List<Element> {
    val result = mutableListOf<Element>()
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child is Element) result += child
    }
    return result
}

private fun normalizeResourceId(value: String): String =
    value.trim().removePrefix("@").removePrefix("0x").lowercase()

private fun isValidRow(value: String?, expectedLength: Int): Boolean {
    if (value == null || value.length != expectedLength) return false
    val normalized = value.lowercase()
    return normalized.all { it in 'a'..'z' } && normalized.toSet().size == expectedLength
}

/**
 * Adds editable row-order fields to Morphe's patch options, then updates only
 * Gboard's QWERTZ letter mappings. QWERTY, Colemak and other layouts stay intact.
 */
@Suppress("unused")
val customizeQwertzLayoutPatch = resourcePatch(
    name = "QWERTZ: customize all letter rows",
    description = "Lets you set the 10/9/7 letter order of the QWERTZ keyboard before patching.",
    default = true,
) {
    compatibleWith(gboardCompatibility)

    val topRow by stringOption(
        key = "Fileira superior (10 letras)",
        default = "qwertzuiop",
        description = "Digite 10 letras diferentes, sem espaços.",
    ) { value -> isValidRow(value, 10) }

    val middleRow by stringOption(
        key = "Fileira do meio (9 letras)",
        default = "asdfghjkl",
        description = "Digite 9 letras diferentes, sem espaços.",
    ) { value -> isValidRow(value, 9) }

    val bottomRow by stringOption(
        key = "Fileira inferior (7 letras)",
        default = "yxcvbnm",
        description = "Digite 7 letras diferentes, sem espaços.",
    ) { value -> isValidRow(value, 7) }

    execute {
        val rows = listOf(
            requireNotNull(topRow).lowercase(),
            requireNotNull(middleRow).lowercase(),
            requireNotNull(bottomRow).lowercase(),
        )
        val allLetters = rows.joinToString("")
        check(allLetters.length == 26 && allLetters.toSet() == ('a'..'z').toSet()) {
            "The three rows must contain every letter from a to z exactly once."
        }

        val positions = listOf(topRowViews, middleRowViews, bottomRowViews)
        check(positions.map { it.size } == listOf(10, 9, 7)) {
            "The expected QWERTZ keyboard rows (10/9/7 keys) were not found."
        }

        val letterToIndex = DEFAULT_LAYOUT.withIndex().associate { it.value to it.index }

        document("res/cZ-.xml").use { mappingDocument ->
            val groups = mappingDocument.documentElement.children()
                .filter { it.tagName == "key_mapping" }

            check(groups.size == 2) {
                "Expected the normal and SHIFT,CAPS QWERTZ mapping groups; found ${groups.size}."
            }

            groups.forEach { group ->
                val isUppercase = group.getAttribute("state").contains("SHIFT")
                val keyIds = if (isUppercase) uppercaseKeyIds else lowercaseKeyIds
                val directMappings = group.children().filter { it.tagName == "mapping" }

                fun update(viewId: String, letter: Char) {
                    val matches = directMappings.filter {
                        normalizeResourceId(it.getAttribute("view_id")) ==
                            normalizeResourceId(viewId)
                    }
                    check(matches.size == 1) {
                        "Expected exactly one QWERTZ mapping for view $viewId; found ${matches.size}."
                    }

                    val index = letterToIndex[letter]
                        ?: error("Unsupported letter in custom layout: $letter")
                    matches.single().setAttribute("key_id", keyIds[index])
                }

                rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { letterIndex, letter ->
                        update(positions[rowIndex][letterIndex], letter)
                    }
                }

                // Keep the duplicated centre keys aligned in split mode.
                update(SPLIT_HOME_DUPLICATE_VIEW, rows[1][4])
                update(SPLIT_BOTTOM_DUPLICATE_VIEW, rows[2][3])
            }
        }
    }
}
