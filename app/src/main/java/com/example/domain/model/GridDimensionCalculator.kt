package com.example.domain.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GridDimensions(
    val cols: Int,
    val rows: Int,
    val cellSizeDp: Float,
    val offsetX: Float,
    val offsetY: Float
)

object GridDimensionCalculator {

    const val MIN_CELL_SIZE_DP = 16f
    const val MIN_PORTRAIT_COLS = 8
    const val MIN_PORTRAIT_ROWS = 14
    const val MAX_EXPERT_COLS = 40
    const val MAX_EXPERT_ROWS = 70

    /**
     * Calculates optimal grid rows, columns and cell size based on screen aspect ratio
     * and level progression, strictly enforcing the 16dp minimum cell size limit.
     */
    fun calculate(
        levelNumber: Int,
        availableWidthDp: Float,
        availableHeightDp: Float,
        isDailyChallenge: Boolean = false
    ): GridDimensions {
        val width = max(100f, availableWidthDp)
        val height = max(100f, availableHeightDp)
        val aspectRatio = height / width

        val maxAllowedCols = (width / MIN_CELL_SIZE_DP).toInt().coerceAtLeast(6)
        val maxAllowedRows = (height / MIN_CELL_SIZE_DP).toInt().coerceAtLeast(6)

        // Target cell count progression: Level 1 ~112 cells, growing by ~32 cells every 2 levels
        val effectiveLevel = if (isDailyChallenge) 25 else levelNumber.coerceAtLeast(1)
        val targetCellCount = (112 + ((effectiveLevel - 1) / 2) * 56)
            .coerceAtMost(MAX_EXPERT_COLS * MAX_EXPERT_ROWS)

        var idealCols: Int
        var idealRows: Int

        if (aspectRatio >= 1.0f) {
            // Portrait orientation: taller than wide
            idealCols = sqrt(targetCellCount / aspectRatio).roundToInt()
            idealRows = (idealCols * aspectRatio).roundToInt()

            if (effectiveLevel == 1) {
                idealCols = max(idealCols, MIN_PORTRAIT_COLS)
                idealRows = max(idealRows, MIN_PORTRAIT_ROWS)
            }
        } else {
            // Landscape orientation: wider than tall
            idealRows = sqrt(targetCellCount * aspectRatio).roundToInt()
            idealCols = (idealRows / aspectRatio).roundToInt()

            if (effectiveLevel == 1) {
                idealRows = max(idealRows, MIN_PORTRAIT_COLS)
                idealCols = max(idealCols, MIN_PORTRAIT_ROWS)
            }
        }

        // Clamp to screen limits to guarantee cellSize >= 16dp
        val finalCols = idealCols.coerceIn(6, min(maxAllowedCols, MAX_EXPERT_COLS))
        val finalRows = idealRows.coerceIn(6, min(maxAllowedRows, MAX_EXPERT_ROWS))

        val cellSize = min(width / finalCols, height / finalRows)
        val leftoverX = (width - finalCols * cellSize) / 2f
        val leftoverY = (height - finalRows * cellSize) / 2f

        return GridDimensions(
            cols = finalCols,
            rows = finalRows,
            cellSizeDp = cellSize,
            offsetX = leftoverX,
            offsetY = leftoverY
        )
    }
}
