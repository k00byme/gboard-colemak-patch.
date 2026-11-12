package app.gboard.cafe

/**
 * Original Gboard-side effects. Names describe behavior, not Samsung class names.
 */
enum class KeyEffect {
    NONE,
    COLOR_FLASH,
    OUTLINE_PULSE,
    RIPPLE,
    BOUNCE,
    SPARK_PARTICLES,
    PAINT_SPREAD,
}

enum class BoardEffect {
    NONE,
    SPARK_SPIN,
    RING_SPREAD,
    BLOB_SPREAD,
    OUTLINE_WAVE,
    PAINT_GUN,
}

/** Values are bounded here so malformed settings cannot create extreme animations. */
data class EffectConfig(
    val keyEffect: KeyEffect = KeyEffect.NONE,
    val boardEffect: BoardEffect = BoardEffect.NONE,
    val colorArgb: Int = 0xFFCFB08E.toInt(),
    val durationMs: Int = 260,
    val intensity: Float = 1f,
) {
    init {
        require(durationMs in 80..1200) { "durationMs must be between 80 and 1200" }
        require(intensity.isFinite() && intensity in 0f..2f) {
            "intensity must be finite and between 0 and 2"
        }
    }
}

/**
 * A stable key identity is independent of its current row/position. This lets a
 * custom layout survive reordering and allows a reset to the original definition.
 */
data class EditableKey(
    val id: String,
    val label: String,
    val pressData: String,
    val longPressData: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Key id cannot be blank" }
        require(label.isNotEmpty()) { "Key label cannot be empty" }
        require(pressData.isNotEmpty()) { "Key pressData cannot be empty" }
    }
}

data class EditableKeyRow(
    val id: String,
    val keys: List<EditableKey>,
) {
    init {
        require(id.isNotBlank()) { "Row id cannot be blank" }
        require(keys.isNotEmpty()) { "A keyboard row cannot be empty" }
    }
}

data class EditableKeyboardLayout(
    val schemaVersion: Int = 1,
    val layoutId: String,
    val rows: List<EditableKeyRow>,
) {
    init {
        require(schemaVersion == 1) { "Unsupported layout schema version: $schemaVersion" }
        require(layoutId.isNotBlank()) { "Layout id cannot be blank" }
        require(rows.isNotEmpty()) { "A keyboard layout must contain at least one row" }

        val rowIds = rows.map { it.id }
        require(rowIds.size == rowIds.toSet().size) { "Duplicate row ids are not allowed" }

        val keyIds = rows.flatMap { row -> row.keys.map { it.id } }
        require(keyIds.size == keyIds.toSet().size) { "Duplicate key ids are not allowed" }
    }

    fun moveKey(keyId: String, targetRowId: String, targetIndex: Int): EditableKeyboardLayout {
        require(targetIndex >= 0) { "Target index cannot be negative" }
        val source = rows.indexOfFirst { row -> row.keys.any { it.id == keyId } }
        val target = rows.indexOfFirst { it.id == targetRowId }
        require(source >= 0) { "Unknown key id: $keyId" }
        require(target >= 0) { "Unknown row id: $targetRowId" }

        val movedKey = rows[source].keys.first { it.id == keyId }
        val nextRows = rows.mapIndexed { index, row ->
            if (index == source) row.copy(keys = row.keys.filterNot { it.id == keyId }) else row
        }.toMutableList()

        val targetKeys = nextRows[target].keys.toMutableList()
        require(targetIndex <= targetKeys.size) { "Target index is outside the destination row" }
        targetKeys.add(targetIndex, movedKey)
        nextRows[target] = nextRows[target].copy(keys = targetKeys)

        // Empty rows are invalid; do not silently delete a user's row.
        require(nextRows.all { it.keys.isNotEmpty() }) {
            "Moving this key would leave an empty row"
        }
        return copy(rows = nextRows)
    }

    fun removeKey(keyId: String): EditableKeyboardLayout {
        require(rows.any { row -> row.keys.any { it.id == keyId } }) {
            "Unknown key id: $keyId"
        }
        val nextRows = rows.map { row ->
            row.copy(keys = row.keys.filterNot { it.id == keyId })
        }
        require(nextRows.all { it.keys.isNotEmpty() }) {
            "Cannot remove the last key from a row"
        }
        return copy(rows = nextRows)
    }
}
