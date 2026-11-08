package com.example.domain.model

data class LevelInfo(
    val levelNumber: Int,
    val width: Int,
    val height: Int,
    val difficulty: Difficulty,
    val algorithm: AlgorithmType
) {
    companion object {
        fun resolve(level: Int): LevelInfo {
            val clampedLevel = level.coerceAtLeast(1)
            val difficulty = Difficulty.forLevel(clampedLevel)

            val minSize = difficulty.minGridSize
            val maxSize = difficulty.maxGridSize
            val range = difficulty.levelRange
            val progress = (clampedLevel - range.first).toFloat() / (range.last - range.first).coerceAtLeast(1)
            val baseSize = (minSize + progress * (maxSize - minSize)).toInt().coerceIn(minSize, maxSize)

            val width = baseSize
            val height = if (clampedLevel % 4 == 0 && baseSize > 6) baseSize - 1 else baseSize

            val algorithm = when (clampedLevel % 3) {
                1 -> AlgorithmType.BACKTRACKER
                2 -> AlgorithmType.PRIM
                else -> AlgorithmType.KRUSKAL
            }

            return LevelInfo(
                levelNumber = clampedLevel,
                width = width,
                height = height,
                difficulty = difficulty,
                algorithm = algorithm
            )
        }
    }
}
