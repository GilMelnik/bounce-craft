package com.colorbounce.baby

/**
 * Non-persisted state for Creation Mode, merged with [AppSettings] in [GameViewModel] only.
 * Shape pool and random/alternate mode match the settings UI; [fromSettings] seeds them on entry.
 */
data class CreationSession(
    val selectedShapes: Set<ShapeType> = setOf(
        ShapeType.CIRCLE,
        ShapeType.RECTANGLE,
        ShapeType.TRIANGLE,
        ShapeType.ARCH
    ),
    val shapeSelectionMode: ShapeSelectionMode = ShapeSelectionMode.ALTERNATE,
    /** When non-null, new shapes use this HSV; otherwise use default random/HSV behavior. */
    val spawnColor: Triple<Float, Float, Float>? = null,
    val newShapesPinned: Boolean = false,
    val newShapesImmortal: Boolean = false,
    val disableHueWhileDragging: Boolean = false,
    val physicsPaused: Boolean = false
) {
    companion object {
        val default = CreationSession()

        fun fromSettings(settings: AppSettings): CreationSession =
            CreationSession(
                selectedShapes = settings.selectedShapes.toSet(),
                shapeSelectionMode = settings.shapeSelectionMode
            )
    }
}

const val CREATION_MAX_SHAPES = 500
