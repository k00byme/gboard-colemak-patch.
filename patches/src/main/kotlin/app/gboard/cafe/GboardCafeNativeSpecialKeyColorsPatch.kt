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
        // These are ordinary APK assets, not decoded Android resources.
        // listApkEntries confirms their archive paths; get(..., copy = true)
        // extracts their existing bytes into the patch workspace. Passing false
        // here creates a replacement path without copying the original asset,
        // which was the cause of the V9 "asset is missing" failure.
        val commonPath = "assets/theme/style_sheet_color_common.binarypb"
        val defaultPath = "assets/theme/style_sheet_default.binarypb"
        val themeEntries = listApkEntries("assets/theme/")

        check(commonPath in themeEntries) {
            "Gboard APK does not contain $commonPath"
        }
        check(defaultPath in themeEntries) {
            "Gboard APK does not contain $defaultPath"
        }

        val commonSheet = this[commonPath]
        val defaultSheet = this[defaultPath]

        check(commonSheet.isFile && commonSheet.length() > 0L) {
            "Could not extract existing Gboard theme asset: $commonPath"
        }
        check(defaultSheet.isFile && defaultSheet.length() > 0L) {
            "Could not extract existing Gboard theme asset: $defaultPath"
        }

        val commonTargets = setOf(
            "default_borderless_space_bar_color",
            "default_borderless_space_bar_color_pressed",
        )
        val defaultTargets = setOf(
            "default_borderless_space_bar_color_hovered",
            "default_action_key_background_color",
            "default_action_key_background_color_hovered",
            "default_action_key_background_color_pressed",
        )

        commonSheet.writeBytes(
            recolorThemeVariables(commonSheet.readBytes(), commonTargets, "color_common")
        )
        defaultSheet.writeBytes(
            recolorThemeVariables(defaultSheet.readBytes(), defaultTargets, "default")
        )

        println("[Gboard Cafe] Native spacebar and action-key theme variables set to #000000.")
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
    sheetLabel: String,
): ByteArray {
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
        var insertedColor = false

        for (field in fields) {
            // Field 2 stores a literal color; field 3 stores an alias to another
            // variable. Replace either representation with a literal ARGB value.
            if (field.number == 2 || field.number == 3) {
                if (!insertedColor) {
                    rewrittenRecord.write(blackColorField)
                    insertedColor = true
                }
            } else {
                rewrittenRecord.write(field.raw)
            }
        }

        if (!insertedColor) rewrittenRecord.write(blackColorField)
        output.write(encodeGboardProtoField(entry.number, 2, rewrittenRecord.toByteArray()))
        updated.add(variableName!!)
    }

    val missing = requestedNames - updated
    check(missing.isEmpty()) {
        "Gboard $sheetLabel stylesheet did not contain expected variables: ${missing.joinToString()}"
    }

    return output.toByteArray()
}
