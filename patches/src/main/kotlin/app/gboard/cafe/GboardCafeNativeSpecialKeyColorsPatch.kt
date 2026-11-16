package app.gboard.cafe

import app.morphe.patcher.patch.resourcePatch
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Applies Gboard's own theme variables for the two special-key families that
 * are not consistently represented by ordinary View background Drawables.
 *
 * The exact target build is pinned by gboardCafeCompatibility. No drawing or
 * touch hooks are added: Gboard's stylesheet engine paints these key surfaces.
 */
val gboardCafeNativeSpecialKeyColorsPatch = resourcePatch(
    name = "Native Spacebar and Action-Key Colors",
    description = "Sets native spacebar and action-key backgrounds to #000000.",
    default = true,
) {
    compatibleWith(gboardCafeCompatibility)

    execute {
        // The gradient background is not a key color: Gboard applies it through
        // separate .keyboard-background CSS rules. Remove those rules from the
        // gradient and user-image overlays, then make every keyboard surface
        // palette resolve to opaque black. Leave action-key and spacebar colors
        // untouched so the user's selected key theme controls those three keys.
        val themePaths = listApkEntries("assets/theme/")
            .filter {
                it.startsWith("assets/theme/style_sheet_") && it.endsWith(".binarypb")
            }
        check(themePaths.isNotEmpty()) {
            "No Gboard theme stylesheets were found in assets/theme/."
        }

        val backgroundTargets = setOf(
            "default_keyboard_background_primary_color",
            "default_keyboard_background_secondary_color",
            "default_keyboard_background_header_color",
        )

        val updatedNames = LinkedHashSet<String>()
        var changedColorSheets = 0
        var clearedBackgroundSheets = 0

        val overlaysToFlatten = setOf(
            "assets/theme/style_sheet_gradient_dark_override.binarypb",
            "assets/theme/style_sheet_gradient_light_override.binarypb",
            "assets/theme/style_sheet_landscape_user_image_override.binarypb",
            "assets/theme/style_sheet_dynamic_color_rules.binarypb",
        )

        for (path in themePaths) {
            // Default copy=true extracts the original APK asset before editing.
            val asset = this[path]
            check(asset.isFile && asset.length() > 0L) {
                "Could not extract Gboard theme asset: " + path
            }

            val original = asset.readBytes()
            var updated = original
            var changed = false

            if (path in overlaysToFlatten) {
                val flattened = removeKeyboardBackgroundOverrideRules(updated)
                if (flattened.second > 0) {
                    updated = flattened.first
                    clearedBackgroundSheets += 1
                    changed = true
                }
            }

            val recolored = recolorThemeVariables(updated, backgroundTargets)
            if (recolored.second.isNotEmpty()) {
                updated = recolored.first
                updatedNames.addAll(recolored.second)
                changedColorSheets += 1
                changed = true
            }

            if (changed) asset.writeBytes(updated)
        }

        val missing = backgroundTargets - updatedNames
        check(missing.isEmpty()) {
            "Gboard theme stylesheets are missing keyboard background colors: " +
                missing.joinToString()
        }

        check(clearedBackgroundSheets >= 3) {
            "Expected gradient/image keyboard background overrides were not found."
        }

        println(
            "[Gboard Cafe] Keyboard surface forced to #000000; updated " +
                updatedNames.size + " background variables across " +
                changedColorSheets + " stylesheets and removed background " +
                "overlays from " + clearedBackgroundSheets + " stylesheets."
        )
    }
}

/**
 * Removes only whole-keyboard background/image/gradient rules from overlay
 * stylesheets. Other rules in the same asset, especially key-specific styling,
 * are preserved verbatim.
 */
private fun removeKeyboardBackgroundOverrideRules(sheet: ByteArray): Pair<ByteArray, Int> {
    val output = ByteArrayOutputStream()
    var removed = 0

    for (entry in readGboardProtoFields(sheet)) {
        if (entry.number != 1 || entry.wireType != 2 || entry.bytesValue == null) {
            output.write(entry.raw)
            continue
        }

        val selectors = readGboardProtoFields(entry.bytesValue)
            .filter { it.number == 1 && it.wireType == 2 && it.bytesValue != null }
            .map { it.bytesValue!!.toString(StandardCharsets.UTF_8) }

        val isKeyboardSurfaceRule = selectors.any { selector ->
            selector == ".keyboard-background" ||
                selector == ".widget-keyboard.keyboard-background" ||
                selector.startsWith(".widget-keyboard.keyboard-background.") ||
                selector == ".keyboard-background.for-preview"
        }

        if (isKeyboardSurfaceRule) {
            removed++
        } else {
            output.write(entry.raw)
        }
    }

    return output.toByteArray() to removed
}

private const val SPECIAL_KEY_BLACK_ARGB = 0xFF000000L

private data class GboardProtoField(
    val number: Int,
    val wireType: Int,
    val bytesValue: ByteArray?,
    val raw: ByteArray,
)

private fun readGboardProtoVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
    var position = start
    var value = 0L
    var shift = 0
    repeat(10) {
        check(position < bytes.size) { "Truncated Gboard theme protobuf varint." }
        val current = bytes[position++].toInt() and 0xFF
        value = value or ((current and 0x7F).toLong() shl shift)
        if ((current and 0x80) == 0) return value to position
        shift += 7
    }
    error("Invalid Gboard theme protobuf varint.")
}

private fun skipGboardProtoGroup(
    bytes: ByteArray,
    start: Int,
    expectedNumber: Int,
): Int {
    var position = start
    while (position < bytes.size) {
        val (tag, afterTag) = readGboardProtoVarint(bytes, position)
        position = afterTag
        val number = (tag ushr 3).toInt()
        val wireType = (tag and 0x07).toInt()

        when (wireType) {
            0 -> position = readGboardProtoVarint(bytes, position).second
            1 -> position += 8
            2 -> {
                val (length, afterLength) = readGboardProtoVarint(bytes, position)
                check(length <= Int.MAX_VALUE.toLong()) { "Oversized Gboard theme protobuf field." }
                position = afterLength + length.toInt()
            }
            3 -> position = skipGboardProtoGroup(bytes, position, number)
            4 -> {
                check(number == expectedNumber) { "Mismatched Gboard theme protobuf group." }
                return position
            }
            5 -> position += 4
            else -> error("Unsupported Gboard theme protobuf wire type: $wireType")
        }
        check(position <= bytes.size) { "Truncated Gboard theme protobuf group." }
    }
    error("Unclosed Gboard theme protobuf group.")
}

private fun readGboardProtoFields(bytes: ByteArray): List<GboardProtoField> {
    val result = ArrayList<GboardProtoField>()
    var position = 0

    while (position < bytes.size) {
        val start = position
        val (tag, afterTag) = readGboardProtoVarint(bytes, position)
        position = afterTag
        val number = (tag ushr 3).toInt()
        val wireType = (tag and 0x07).toInt()
        check(number > 0) { "Invalid Gboard theme protobuf field number." }

        var bytesValue: ByteArray? = null
        when (wireType) {
            0 -> position = readGboardProtoVarint(bytes, position).second
            1 -> position += 8
            2 -> {
                val (length, afterLength) = readGboardProtoVarint(bytes, position)
                check(length <= Int.MAX_VALUE.toLong()) { "Oversized Gboard theme protobuf field." }
                val end = afterLength + length.toInt()
                check(end <= bytes.size) { "Truncated Gboard theme protobuf field." }
                bytesValue = bytes.copyOfRange(afterLength, end)
                position = end
            }
            3 -> position = skipGboardProtoGroup(bytes, position, number)
            4 -> error("Unexpected Gboard theme protobuf end-group.")
            5 -> position += 4
            else -> error("Unsupported Gboard theme protobuf wire type: $wireType")
        }
        check(position <= bytes.size) { "Truncated Gboard theme protobuf field." }
        result.add(
            GboardProtoField(
                number = number,
                wireType = wireType,
                bytesValue = bytesValue,
                raw = bytes.copyOfRange(start, position),
            )
        )
    }

    return result
}

private fun encodeGboardProtoVarint(input: Long): ByteArray {
    require(input >= 0L) { "Negative Gboard theme protobuf varint." }
    val output = ByteArrayOutputStream()
    var value = input
    while ((value and -128L) != 0L) {
        output.write(((value and 0x7FL) or 0x80L).toInt())
        value = value ushr 7
    }
    output.write(value.toInt())
    return output.toByteArray()
}

private fun encodeGboardProtoField(
    number: Int,
    wireType: Int,
    value: ByteArray,
): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(encodeGboardProtoVarint(((number shl 3) or wireType).toLong()))
    if (wireType == 2) output.write(encodeGboardProtoVarint(value.size.toLong()))
    output.write(value)
    return output.toByteArray()
}

private fun encodeGboardProtoVarintField(number: Int, value: Long): ByteArray {
    return encodeGboardProtoVarint(((number shl 3)).toLong()) +
        encodeGboardProtoVarint(value)
}

private fun blackGboardProtoColorField(): ByteArray {
    val colorMessage = encodeGboardProtoVarintField(1, SPECIAL_KEY_BLACK_ARGB)
    return encodeGboardProtoField(2, 2, colorMessage)
}

private fun recolorThemeVariables(
    sheet: ByteArray,
    requestedNames: Set<String>,
): Pair<ByteArray, Set<String>> {
    val output = ByteArrayOutputStream()
    val updated = LinkedHashSet<String>()
    val blackColorField = blackGboardProtoColorField()

    for (entry in readGboardProtoFields(sheet)) {
        val record = entry.bytesValue
        if (entry.wireType != 2 || record == null) {
            output.write(entry.raw)
            continue
        }

        val fields = readGboardProtoFields(record)
        val variableName = fields.firstOrNull {
            it.number == 1 && it.wireType == 2 && it.bytesValue != null
        }?.bytesValue?.toString(StandardCharsets.UTF_8)

        if (variableName !in requestedNames) {
            output.write(entry.raw)
            continue
        }

        val rewrittenRecord = ByteArrayOutputStream()
        // For this key-surface patch, remove the literal/alias/overlay fields
        // (2, 3 and 5) and replace them with a single opaque black ARGB value.
        // Keeping a theme's pressed/hovered interpolation field would allow it
        // to blend the new value back toward the old gray/blue color.
        for (field in fields) {
            if (field.number != 2 && field.number != 3 && field.number != 5) {
                rewrittenRecord.write(field.raw)
            }
        }
        rewrittenRecord.write(blackColorField)

        output.write(encodeGboardProtoField(entry.number, 2, rewrittenRecord.toByteArray()))
        updated.add(variableName!!)
    }

    return output.toByteArray() to updated
}
