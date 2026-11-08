package com.example.domain.solver

import com.example.domain.model.Cell
import com.example.domain.model.Direction
import com.example.domain.model.KeyDoor
import com.example.domain.model.Maze

/**
 * Single source of truth for how the ball behaves in SWIPE mode.
 *
 * The ViewModel (live gameplay) and [MazeSolver.solveSwipeMoves] (optimal move count used for
 * star rating) both go through this object, so the "optimal" number of swipes always matches the
 * swipes a player can actually make. Before this, the optimal value was a count of direction
 * changes, which ignores the extra stops at junctions - so perfect play could not reach 3 stars.
 */
object SlideSimulator {

    /** Where the ball ends up after a slide, once key pickup and portals are applied. */
    data class Landing(
        val row: Int,
        val col: Int,
        val hasKey: Boolean,
        val pickedUpKey: Boolean,
        val teleported: Boolean
    )

    fun isBlocked(cell: Cell, direction: Direction, hasKey: Boolean, keyDoor: KeyDoor?): Boolean {
        if (cell.hasWall(direction)) return true
        return keyDoor != null && !hasKey && keyDoor.blocks(cell.row, cell.col, direction)
    }

    /**
     * Cells the ball passes through (excluding the start cell) when swiped in [direction].
     * The slide stops at: a wall/door, the goal, the key, a portal, or any cell that opens
     * sideways (a decision point). An empty list means the swipe is blocked immediately.
     */
    fun computeSlide(
        maze: Maze,
        fromRow: Int,
        fromCol: Int,
        direction: Direction,
        hasKey: Boolean
    ): List<Pair<Int, Int>> {
        val keyDoor = maze.mechanics.keyDoor
        val portal = maze.mechanics.portal
        val sideDirs = when (direction) {
            Direction.UP, Direction.DOWN -> listOf(Direction.LEFT, Direction.RIGHT)
            Direction.LEFT, Direction.RIGHT -> listOf(Direction.UP, Direction.DOWN)
        }

        val steps = ArrayList<Pair<Int, Int>>()
        var row = fromRow
        var col = fromCol

        while (true) {
            val cell = maze.getCell(row, col) ?: break
            if (isBlocked(cell, direction, hasKey, keyDoor)) break

            val nextRow = row + direction.dRow
            val nextCol = col + direction.dCol
            val nextCell = maze.getCell(nextRow, nextCol) ?: break

            steps.add(Pair(nextRow, nextCol))
            row = nextRow
            col = nextCol

            if (row == maze.end.row && col == maze.end.col) break
            if (keyDoor != null && !hasKey && row == keyDoor.keyRow && col == keyDoor.keyCol) break
            if (portal != null && portal.destinationFor(row, col) != null) break
            if (sideDirs.any { !isBlocked(nextCell, it, hasKey, keyDoor) }) break
        }
        return steps
    }

    /** Applies key pickup first, then portal teleport - same order as live gameplay. */
    fun resolveLanding(maze: Maze, row: Int, col: Int, hasKey: Boolean): Landing {
        val keyDoor = maze.mechanics.keyDoor
        val pickedUp = keyDoor != null && !hasKey && row == keyDoor.keyRow && col == keyDoor.keyCol
        val dest = maze.mechanics.portal?.destinationFor(row, col)
        return Landing(
            row = dest?.first ?: row,
            col = dest?.second ?: col,
            hasKey = hasKey || pickedUp,
            pickedUpKey = pickedUp,
            teleported = dest != null
        )
    }
}
