package com.example.domain.model

enum class Difficulty(
    val title: String,
    val minGridSize: Int,
    val maxGridSize: Int,
    val levelRange: IntRange,
    val minSolutionRatio: Float,
    val initialHints: Int
) {
    EASY("Easy", 8, 14, 1..50, 0.22f, 3),
    MEDIUM("Medium", 14, 24, 51..100, 0.28f, 2),
    HARD("Hard", 24, 34, 101..160, 0.34f, 1),
    EXPERT("Expert", 34, 45, 161..200, 0.38f, 1);

    companion object {
        fun forLevel(level: Int): Difficulty {
            return when {
                level <= 50 -> EASY
                level <= 100 -> MEDIUM
                level <= 160 -> HARD
                else -> EXPERT
            }
        }

        fun determineMechanics(level: Int): LevelMechanics {
            val hasFog = when {
                level in 6..10 -> true
                level in 26..30 -> true
                level >= 31 && (level % 2 == 1) -> true
                else -> false
            }

            val hasKeyDoor = when {
                level in 11..15 -> true
                level in 31..35 -> true
                level >= 36 && (level % 3 == 0) -> true
                else -> false
            }

            val hasPortal = when {
                level in 16..20 -> true
                level in 36..40 -> true
                level >= 41 && (level % 3 == 1) -> true
                else -> false
            }

            val hasHazards = when {
                level in 21..25 -> true
                level >= 42 && (level % 3 == 2) -> true
                else -> false
            }

            val isBraid = level >= 20 && (level % 4 == 0)

            return LevelMechanics(
                hasFogOfWar = hasFog,
                keyDoor = if (hasKeyDoor) KeyDoor(0, 0, 0, 0, Direction.DOWN) else null, // Placeholder coords filled during generation
                portal = if (hasPortal) Portal(1, 0, 0, 0, 0) else null,
                hazards = emptyList(), // Concrete patrols are filled during generation
                isBraid = isBraid,
                hasHazards = hasHazards
            )
        }
    }
}
