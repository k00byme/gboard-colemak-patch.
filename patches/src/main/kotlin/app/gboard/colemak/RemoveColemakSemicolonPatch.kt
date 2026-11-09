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

// In the stock QWERTZ XML, letter mappings are ordered by physical row.
// The two duplicated split-mode keys follow the 26 normal letter mappings.
private fun Element.children(): List<Element> {
    val result = mutableListOf<Element>()
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child is Element) result += child
    }
    return result
}

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

        val letterToIndex = DEFAULT_LAYOUT.withIndex().associate { it.value to it.index }
        val customLetters = rows.joinToString("") + rows[1][4] + rows[2][3]

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

                // The normal group stores 26 letters, then the two split-mode
                // duplicate keys, followed by one unrelated mapping.
                // The shifted group has one extra non-letter mapping before
                // the bottom row, so its letter indices skip slot 19.
                val letterSlots = if (isUppercase) {
                    (0..18).toList() + (20..28).toList()
                } else {
                    (0..27).toList()
                }

                check(directMappings.size == 29 && letterSlots.size == customLetters.length) {
                    "Unexpected QWERTZ mapping structure: ${directMappings.size} mappings."
                }

                letterSlots.forEachIndexed { letterIndex, mappingIndex ->
                    val letter = customLetters[letterIndex]
                    val keyIndex = letterToIndex[letter]
                        ?: error("Unsupported letter in custom layout: $letter")
                    directMappings[mappingIndex].setAttribute("key_id", keyIds[keyIndex])
                }
            }
        }
    }
}
