package com.example.domain.model

/**
 * Cardinal directions for grid navigation and maze wall encoding.
 * [bit] represents the wall mask bit for TOP (1), RIGHT (2), BOTTOM (4), LEFT (8).
 */
enum class Direction(val dRow: Int, val dCol: Int, val bit: Int) {
    UP(-1, 0, 1),
    RIGHT(0, 1, 2),
    DOWN(1, 0, 4),
    LEFT(0, -1, 8);

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            RIGHT -> LEFT
            DOWN -> UP
            LEFT -> RIGHT
        }
}
