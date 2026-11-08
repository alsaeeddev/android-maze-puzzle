package com.example.domain.solver

import com.example.domain.model.Cell
import com.example.domain.model.Direction
import com.example.domain.model.KeyDoor
import com.example.domain.model.Maze
import com.example.domain.model.Portal
import java.util.ArrayDeque

object MazeSolver {

    data class SolveResult(
        val isSolvable: Boolean,
        val path: List<Cell>,
        val stepsCount: Int,
        val junctionMoves: Int,
        val keyFoundBeforeDoor: Boolean = true
    )

    /**
     * Solves the maze using Multi-State Breadth-First Search (BFS).
     * State is represented as (row, col, hasKey) to account for keys, doors, and portals.
     */
    fun solve(
        grid: List<List<Cell>>,
        startRow: Int,
        startCol: Int,
        endRow: Int,
        endCol: Int,
        keyDoor: KeyDoor? = null,
        portal: Portal? = null
    ): SolveResult {
        val height = grid.size
        if (height == 0) return SolveResult(false, emptyList(), 0, 0)
        val width = grid[0].size
        if (width == 0) return SolveResult(false, emptyList(), 0, 0)

        // Key states: 0 = without key, 1 = with key
        val visited = Array(height) { Array(width) { BooleanArray(2) } }

        // Encode state: (row * width + col) * 2 + (if hasKey 1 else 0)
        val parent = HashMap<Int, Int>()

        fun encode(r: Int, c: Int, k: Boolean): Int {
            return (r * width + c) * 2 + (if (k) 1 else 0)
        }

        fun decode(code: Int): Triple<Int, Int, Boolean> {
            val hasK = (code and 1) == 1
            val cellIdx = code shr 1
            val r = cellIdx / width
            val c = cellIdx % width
            return Triple(r, c, hasK)
        }

        val startHasKey = keyDoor != null && (startRow == keyDoor.keyRow && startCol == keyDoor.keyCol)
        val startCode = encode(startRow, startCol, startHasKey)
        visited[startRow][startCol][if (startHasKey) 1 else 0] = true

        val queue = ArrayDeque<Int>()
        queue.add(startCode)

        var endCode: Int? = null

        while (queue.isNotEmpty()) {
            val currCode = queue.removeFirst()
            val (currR, currC, hasKey) = decode(currCode)

            if (currR == endRow && currC == endCol) {
                endCode = currCode
                break
            }

            val currentCell = grid[currR][currC]

            // 1. Standard movements to adjacent cells
            for (dir in Direction.entries) {
                if (!currentCell.hasWall(dir)) {
                    // Check if passage is blocked by a locked door
                    val isDoorPassage = keyDoor != null && !hasKey && (
                        (currR == keyDoor.doorRow && currC == keyDoor.doorCol && dir == keyDoor.doorDirection) ||
                        (currR == keyDoor.doorRow + keyDoor.doorDirection.dRow &&
                         currC == keyDoor.doorCol + keyDoor.doorDirection.dCol &&
                         dir == keyDoor.doorDirection.opposite)
                    )

                    if (!isDoorPassage) {
                        val nr = currR + dir.dRow
                        val nc = currC + dir.dCol
                        if (nr in 0 until height && nc in 0 until width) {
                            val newHasKey = hasKey || (keyDoor != null && nr == keyDoor.keyRow && nc == keyDoor.keyCol)
                            val keyIdx = if (newHasKey) 1 else 0

                            if (!visited[nr][nc][keyIdx]) {
                                visited[nr][nc][keyIdx] = true
                                val nextCode = encode(nr, nc, newHasKey)
                                parent[nextCode] = currCode
                                queue.add(nextCode)
                            }
                        }
                    }
                }
            }

            // 2. Portal teleportation
            if (portal != null) {
                val dest = portal.destinationFor(currR, currC)
                if (dest != null) {
                    val (pr, pc) = dest
                    val newHasKey = hasKey || (keyDoor != null && pr == keyDoor.keyRow && pc == keyDoor.keyCol)
                    val keyIdx = if (newHasKey) 1 else 0

                    if (!visited[pr][pc][keyIdx]) {
                        visited[pr][pc][keyIdx] = true
                        val nextCode = encode(pr, pc, newHasKey)
                        parent[nextCode] = currCode
                        queue.add(nextCode)
                    }
                }
            }
        }

        if (endCode == null) {
            return SolveResult(false, emptyList(), 0, 0, false)
        }

        // Reconstruct path
        val pathList = mutableListOf<Cell>()
        var curr: Int? = endCode
        var visitedKey = false

        while (curr != null) {
            val (r, c, k) = decode(curr)
            if (k) visitedKey = true
            pathList.add(grid[r][c])
            if (curr == startCode) break
            curr = parent[curr]
        }
        pathList.reverse()

        // Count turns / junction decisions
        var turns = 0
        if (pathList.size > 1) {
            turns = 1
            var prevDir: Direction? = null
            for (i in 0 until pathList.size - 1) {
                val c1 = pathList[i]
                val c2 = pathList[i + 1]
                val dr = c2.row - c1.row
                val dc = c2.col - c1.col
                val dir = Direction.entries.firstOrNull { it.dRow == dr && it.dCol == dc }
                if (prevDir != null && dir != null && dir != prevDir) {
                    turns++
                }
                prevDir = dir
            }
        }

        val keyBeforeDoorOk = if (keyDoor != null) visitedKey else true

        return SolveResult(
            isSolvable = true,
            path = pathList,
            stepsCount = pathList.size - 1,
            junctionMoves = turns,
            keyFoundBeforeDoor = keyBeforeDoorOk
        )
    }

    /**
     * Minimum number of swipes needed to reach the goal in SWIPE mode, found with a BFS over
     * (row, col, hasKey) states using the exact movement rules of [SlideSimulator].
     * Returns null if the goal cannot be reached (should never happen for generated mazes).
     * Hazards are ignored (they only reset the ball, they never change reachability).
     */
    fun solveSwipeMoves(maze: Maze): Int? {
        val keyDoor = maze.mechanics.keyDoor
        val visited = Array(maze.height) { Array(maze.width) { BooleanArray(2) } }
        val queue = ArrayDeque<IntArray>() // [row, col, hasKey(0/1), swipesSoFar]

        val startHasKey = keyDoor != null &&
            maze.start.row == keyDoor.keyRow && maze.start.col == keyDoor.keyCol
        visited[maze.start.row][maze.start.col][if (startHasKey) 1 else 0] = true
        queue.add(intArrayOf(maze.start.row, maze.start.col, if (startHasKey) 1 else 0, 0))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val row = node[0]
            val col = node[1]
            val keyFlag = node[2]
            val swipes = node[3]
            if (row == maze.end.row && col == maze.end.col) return swipes

            for (dir in Direction.entries) {
                val steps = SlideSimulator.computeSlide(maze, row, col, dir, keyFlag == 1)
                if (steps.isEmpty()) continue
                val last = steps.last()
                val landing = SlideSimulator.resolveLanding(maze, last.first, last.second, keyFlag == 1)
                val landingKey = if (landing.hasKey) 1 else 0
                if (!visited[landing.row][landing.col][landingKey]) {
                    visited[landing.row][landing.col][landingKey] = true
                    queue.add(intArrayOf(landing.row, landing.col, landingKey, swipes + 1))
                }
            }
        }
        return null
    }

    /**
     * Finds the farthest cell from (startRow, startCol) and returns the cell and distance.
     * Used for the double-BFS tree diameter algorithm.
     */
    fun findFarthestCell(
        grid: List<List<Cell>>,
        startRow: Int,
        startCol: Int
    ): Pair<Cell, Int> {
        val height = grid.size
        val width = grid[0].size
        val visited = Array(height) { BooleanArray(width) }
        val dist = Array(height) { IntArray(width) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        visited[startRow][startCol] = true
        dist[startRow][startCol] = 0
        queue.add(Pair(startRow, startCol))

        var farthestCell = grid[startRow][startCol]
        var maxDist = 0

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            val d = dist[r][c]
            if (d > maxDist) {
                maxDist = d
                farthestCell = grid[r][c]
            }

            val cell = grid[r][c]
            for (dir in Direction.entries) {
                if (!cell.hasWall(dir)) {
                    val nr = r + dir.dRow
                    val nc = c + dir.dCol
                    if (nr in 0 until height && nc in 0 until width && !visited[nr][nc]) {
                        visited[nr][nc] = true
                        dist[nr][nc] = d + 1
                        queue.add(Pair(nr, nc))
                    }
                }
            }
        }

        return Pair(farthestCell, maxDist)
    }
}
