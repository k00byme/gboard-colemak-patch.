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
        // Gboard composes its appearance from several binary protobuf stylesheets.
        // A change to the base/default sheet alone can be overridden by Material 3,
        // Dynamic Color, and the selected color-pack stylesheet. Patch every sheet
        // that defines one of the native key-surface variables.
        val targets = setOf(
            "default_action_key_background_color",
            "default_action_key_background_color_hovered",
            "default_action_key_background_color_pressed",
            "default_borderless_space_bar_color",
            "default_borderless_space_bar_color_hovered",
            "default_borderless_space_bar_color_pressed",
            "default_bordered_key_color",
            "default_bordered_key_color_hovered",
            "default_bordered_key_color_pressed",
            "default_bordered_key_dark_color",
            "default_bordered_key_dark_color_hovered",
            "default_bordered_key_dark_color_pressed",
            "default_pill_shaped_key_color",
            "default_pill_shaped_key_color_hovered",
            "default_pill_shaped_key_color_pressed",
        )

        val themePaths = listApkEntries("assets/theme/")
            .filter {
                it.startsWith("assets/theme/style_sheet_") && it.endsWith(".binarypb")
            }
        check(themePaths.isNotEmpty()) {
            "No Gboard theme stylesheets were found in assets/theme/."
        }

        val updatedNames = LinkedHashSet<String>()
        var changedSheets = 0

        for (path in themePaths) {
            // The default copy=true extracts the original APK asset before editing it.
            val asset = this[path]
            check(asset.isFile) {
                "Could not extract Gboard theme asset: " + path
            }

            val result = recolorThemeVariables(asset.readBytes(), targets)
            if (result.second.isNotEmpty()) {
                asset.writeBytes(result.first)
                updatedNames.addAll(result.second)
                changedSheets++
            }
        }

        val required = setOf(
            "default_action_key_background_color",
            "default_borderless_space_bar_color",
            "default_bordered_key_color",
            "default_bordered_key_dark_color",
            "default_pill_shaped_key_color",
        )
        val missing = required - updatedNames
        check(missing.isEmpty()) {
            "Gboard theme stylesheets are missing required key colors: " + missing.joinToString()
        }

        println(
            "[Gboard Cafe] Set native key surfaces to #000000 in " +
                changedSheets + " theme stylesheets; variables updated: " +
                updatedNames.size
        )
    }
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
