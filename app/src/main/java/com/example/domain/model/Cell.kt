package com.example.domain.model

/**
 * Immutable representation of a single cell in the maze grid.
 * [walls] is a bitmask where:
 * bit 1 = TOP wall present
 * bit 2 = RIGHT wall present
 * bit 4 = BOTTOM wall present
 * bit 8 = LEFT wall present
 * A closed cell with all 4 walls has walls = 15.
 */
data class Cell(
    val row: Int,
    val col: Int,
    val walls: Int = 15
) {
    fun hasWall(direction: Direction): Boolean = (walls and direction.bit) != 0

    fun withWallRemoved(direction: Direction): Cell =
        copy(walls = walls and direction.bit.inv())

    fun withWallAdded(direction: Direction): Cell =
        copy(walls = walls or direction.bit)

    val isTopClosed: Boolean get() = hasWall(Direction.UP)
    val isRightClosed: Boolean get() = hasWall(Direction.RIGHT)
    val isBottomClosed: Boolean get() = hasWall(Direction.DOWN)
    val isLeftClosed: Boolean get() = hasWall(Direction.LEFT)
}
