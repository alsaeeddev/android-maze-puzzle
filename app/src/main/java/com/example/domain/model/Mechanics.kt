package com.example.domain.model

/**
 * Key and Door mechanic. The door blocks passage until the matching key is collected.
 */
data class KeyDoor(
    val keyRow: Int,
    val keyCol: Int,
    val doorRow: Int,
    val doorCol: Int,
    val doorDirection: Direction,
    val isCollected: Boolean = false
) {
    /**
     * True when stepping from ([row], [col]) in [direction] would cross this door
     * (the door is a wall segment between two neighbouring cells, so it blocks both ways).
     */
    fun blocks(row: Int, col: Int, direction: Direction): Boolean =
        (row == doorRow && col == doorCol && direction == doorDirection) ||
            (row == doorRow + doorDirection.dRow &&
                col == doorCol + doorDirection.dCol &&
                direction == doorDirection.opposite)
}

/**
 * Portals teleport the ball between two linked coordinates.
 */
data class Portal(
    val id: Int,
    val rowA: Int,
    val colA: Int,
    val rowB: Int,
    val colB: Int
) {
    fun destinationFor(row: Int, col: Int): Pair<Int, Int>? {
        return when {
            row == rowA && col == colA -> Pair(rowB, colB)
            row == rowB && col == colB -> Pair(rowA, colA)
            else -> null
        }
    }
}

/**
 * Moving patrol hazard. Moves back and forth along a fixed path of cells.
 */
data class Hazard(
    val id: Int,
    val path: List<Pair<Int, Int>>,
    val speed: Float = 1.6f // cells per second
) {
    /**
     * Computes the current position (float row, float col) given elapsed seconds.
     */
    fun positionAt(elapsedSeconds: Float): Pair<Float, Float> {
        if (path.isEmpty()) return Pair(0f, 0f)
        if (path.size == 1 || speed <= 0f) {
            return Pair(path[0].first.toFloat(), path[0].second.toFloat())
        }

        val segmentCount = path.size - 1
        val cycleDuration = segmentCount * 2 / speed
        val timeInCycle = (elapsedSeconds % cycleDuration + cycleDuration) % cycleDuration
        val forwardDuration = segmentCount / speed

        return if (timeInCycle <= forwardDuration) {
            val progressInPath = (timeInCycle * speed)
            val segmentIndex = progressInPath.toInt().coerceIn(0, segmentCount - 1)
            val segmentT = (progressInPath - segmentIndex).coerceIn(0f, 1f)
            val p1 = path[segmentIndex]
            val p2 = path[segmentIndex + 1]
            Pair(
                p1.first + (p2.first - p1.first) * segmentT,
                p1.second + (p2.second - p1.second) * segmentT
            )
        } else {
            val returnTime = timeInCycle - forwardDuration
            val progressInPath = (returnTime * speed)
            val segmentIndex = progressInPath.toInt().coerceIn(0, segmentCount - 1)
            val segmentT = (progressInPath - segmentIndex).coerceIn(0f, 1f)
            val p1 = path[path.size - 1 - segmentIndex]
            val p2 = path[path.size - 2 - segmentIndex]
            Pair(
                p1.first + (p2.first - p1.first) * segmentT,
                p1.second + (p2.second - p1.second) * segmentT
            )
        }
    }
}

/**
 * Mechanics active for a specific level.
 */
data class LevelMechanics(
    val hasFogOfWar: Boolean = false,
    val keyDoor: KeyDoor? = null,
    val portal: Portal? = null,
    val hazards: List<Hazard> = emptyList(),
    val isBraid: Boolean = false,
    /**
     * "This level should contain patrolling hazards". The concrete [hazards] can only be
     * placed once the maze exists, so the level definition carries this flag until then.
     * (Previously the flag was dropped, which meant levels 21-25 never got any hazards.)
     */
    val hasHazards: Boolean = false
) {
    val hasAnyMechanic: Boolean
        get() = hasFogOfWar || keyDoor != null || portal != null || hasHazards || hazards.isNotEmpty()
}
