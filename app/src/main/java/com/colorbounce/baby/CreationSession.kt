package com.colorbounce.baby

/**
 * Non-persisted state for Creation Mode, merged with [AppSettings] in [GameViewModel] only.
 */
data class CreationSession(
    val spawnType: ShapeType? = null,
    /** When [spawnType] is null, picks among [AppSettings.selectedShapes] using this mode. */
    val defaultShapeSelectionMode: ShapeSelectionMode = ShapeSelectionMode.ALTERNATE,
    /** When non-null, new shapes use this HSV; otherwise use default random/HSV behavior. */
    val spawnColor: Triple<Float, Float, Float>? = null,
    val newShapesPinned: Boolean = false,
    val newShapesImmortal: Boolean = false,
    val disableHueWhileDragging: Boolean = false,
    val physicsPaused: Boolean = false
) {
    companion object {
        val default = CreationSession()
    }
}

const val CREATION_MAX_SHAPES = 500
