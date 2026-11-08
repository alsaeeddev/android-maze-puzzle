package com.example.domain.generator

import com.example.domain.model.AlgorithmType
import com.example.domain.model.Cell
import com.example.domain.model.Difficulty
import com.example.domain.model.Direction
import com.example.domain.model.Hazard
import com.example.domain.model.KeyDoor
import com.example.domain.model.LevelMechanics
import com.example.domain.model.Maze
import com.example.domain.model.Portal
import com.example.domain.solver.MazeSolver
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

object MazeGenerator {

    /**
     * Generates a deterministic, reproducible maze for [levelNumber] matching [targetWidth] x [targetHeight].
     * Uses the double-BFS diameter algorithm for start/end, enforces maze quality thresholds,
     * places level mechanics (fog, keys/doors, portals, hazards), and validates multi-state solvability.
     *
     * For the daily challenge pass the date (e.g. 20261107) as [dailySeed] so every day gets a
     * different maze. (Previously the seed only depended on the fixed level number 25, so the
     * "daily" maze was identical every single day.)
     */
    suspend fun generate(
        levelNumber: Int,
        targetWidth: Int = 12,
        targetHeight: Int = 20,
        isDailyChallenge: Boolean = false,
        dailySeed: Int = 0
    ): Maze {
        val width = targetWidth.coerceIn(6, 60)
        val height = targetHeight.coerceIn(6, 90)
        val difficulty = if (isDailyChallenge) Difficulty.HARD else Difficulty.forLevel(levelNumber)
        val baseSeed = if (isDailyChallenge) {
            val seedBase = if (dailySeed != 0) dailySeed else levelNumber
            seedBase.toLong() * 999983L + 104729L
        } else {
            levelNumber.toLong() * 31337L + 7919L
        }

        val baseMechanics = if (isDailyChallenge) {
            LevelMechanics(hasFogOfWar = true, isBraid = true)
        } else {
            Difficulty.determineMechanics(levelNumber)
        }

        var bestMaze: Maze? = null
        var bestScore = -1f

        for (attempt in 0 until MAX_ATTEMPTS) {
            coroutineContext.ensureActive()
            val seed = baseSeed + attempt * 1013L
            val random = Random(seed)

            // Select algorithm style with preference for winding corridors
            val algorithm = when {
                attempt % 4 == 0 -> AlgorithmType.BACKTRACKER
                attempt % 4 == 1 -> AlgorithmType.PRIM
                attempt % 4 == 2 -> AlgorithmType.BACKTRACKER
                else -> AlgorithmType.KRUSKAL
            }

            val rawGrid = when (algorithm) {
                AlgorithmType.BACKTRACKER -> generateWindingBacktracker(width, height, random)
                AlgorithmType.PRIM -> generatePrim(width, height, random)
                AlgorithmType.KRUSKAL -> generateKruskal(width, height, random)
            }

            // Apply optional Braid mode (removing controlled subset of dead-ends to create loops)
            val grid = if (baseMechanics.isBraid) {
                applyBraid(rawGrid, width, height, random, braidRatio = 0.18f)
            } else {
                rawGrid
            }

            // Double-BFS trick to find the longest simple path in the tree
            val (farCellA, _) = MazeSolver.findFarthestCell(grid, 0, 0)
            val (farCellB, _) = MazeSolver.findFarthestCell(grid, farCellA.row, farCellA.col)

            val startCell = if (farCellA.row + farCellA.col <= farCellB.row + farCellB.col) farCellA else farCellB
            val endCell = if (startCell == farCellA) farCellB else farCellA

            // Solve base path between start and end
            val initialSolve = MazeSolver.solve(grid, startCell.row, startCell.col, endCell.row, endCell.col)
            if (!initialSolve.isSolvable) continue

            val totalCells = width * height
            val pathRatio = initialSolve.path.size.toFloat() / totalCells

            // Count dead ends and decision junctions
            var deadEndsCount = 0
            var junctionsCount = 0
            for (r in 0 until height) {
                for (c in 0 until width) {
                    val cell = grid[r][c]
                    val openWalls = Direction.entries.count { !cell.hasWall(it) }
                    if (openWalls == 1) deadEndsCount++
                    if (openWalls >= 3) junctionsCount++
                }
            }

            // Check quality metrics: minimum path length ratio and branch count
            val meetsQuality = pathRatio >= difficulty.minSolutionRatio && deadEndsCount >= (totalCells * 0.10f)

            // Setup level mechanics: Key & Door, Portals, Hazards
            val finalMechanics = configureMechanics(
                baseMechanics = baseMechanics,
                grid = grid,
                path = initialSolve.path,
                start = startCell,
                end = endCell,
                random = random,
                width = width,
                height = height
            )

            // Validate multi-state solvability with all mechanics
            val fullSolve = MazeSolver.solve(
                grid = grid,
                startRow = startCell.row,
                startCol = startCell.col,
                endRow = endCell.row,
                endCol = endCell.col,
                keyDoor = finalMechanics.keyDoor,
                portal = finalMechanics.portal
            )
            // Solvable is the hard requirement. "Key is really needed" (door cannot be bypassed)
            // is a quality preference only: braided mazes have loops that can legitimately bypass
            // a door, and previously that rejected every attempt and dropped the level to a
            // plain fallback maze with no mechanics at all.
            if (!fullSolve.isSolvable) continue

            var candidate = Maze(
                levelNumber = levelNumber,
                width = width,
                height = height,
                grid = grid,
                start = startCell,
                end = endCell,
                optimalPath = fullSolve.path,
                optimalMoves = fullSolve.junctionMoves.coerceAtLeast(1),
                mechanics = finalMechanics,
                deadEndsCount = deadEndsCount,
                junctionsCount = junctionsCount,
                isDailyChallenge = isDailyChallenge
            )

            // Exact optimal swipe count (used for the star rating), instead of "number of turns"
            MazeSolver.solveSwipeMoves(candidate)?.let { swipes ->
                candidate = candidate.copy(optimalMoves = swipes.coerceAtLeast(1))
            }

            val keyOk = fullSolve.keyFoundBeforeDoor
            if (keyOk && meetsQuality) return candidate

            val score = pathRatio + if (keyOk) 10f else 0f
            if (score > bestScore) {
                bestScore = score
                bestMaze = candidate
            }
        }

        return bestMaze ?: generateFallback(levelNumber, width, height, isDailyChallenge, baseMechanics.hasFogOfWar)
    }

    private const val MAX_ATTEMPTS = 8

    /**
     * Places keys, doors, portals, and hazards while ensuring accessibility.
     */
    private fun configureMechanics(
        baseMechanics: LevelMechanics,
        grid: List<List<Cell>>,
        path: List<Cell>,
        start: Cell,
        end: Cell,
        random: Random,
        width: Int,
        height: Int
    ): LevelMechanics {
        var keyDoor: KeyDoor? = null
        var portal: Portal? = null
        val hazards = mutableListOf<Hazard>()

        // The door sits on the main path, so decide its index first: portals must know where it is.
        val doorIdx = if (baseMechanics.keyDoor != null && path.size >= 8) {
            (path.size * 0.55f).toInt().coerceIn(3, path.size - 3)
        } else {
            -1
        }

        // 1. Portals (paired teleport cells).
        // With a door present both portal ends are placed BEFORE the door. The old 25% / 75%
        // placement jumped straight over the door at 55%, so the key was not needed in ~70% of
        // generated mazes and generation had to retry (or fall back to a plain maze).
        if (baseMechanics.portal != null && path.size >= 12) {
            val firstIdx: Int
            val secondIdx: Int
            if (doorIdx >= 0) {
                val limit = (doorIdx - 1).coerceAtLeast(2)
                firstIdx = (limit * 0.2f).toInt().coerceAtLeast(1)
                secondIdx = (limit * 0.85f).toInt().coerceAtLeast(firstIdx + 1)
            } else {
                firstIdx = (path.size * 0.25f).toInt()
                secondIdx = (path.size * 0.75f).toInt()
            }
            portal = Portal(
                id = 1,
                rowA = path[firstIdx].row,
                colA = path[firstIdx].col,
                rowB = path[secondIdx].row,
                colB = path[secondIdx].col
            )
        }

        // 2. Key & Door placement (door on main path, key in a branch reachable before the door)
        if (doorIdx >= 0) {
            val doorCell = path[doorIdx]
            val nextCell = path[doorIdx + 1]
            val doorDir = Direction.entries.firstOrNull {
                it.dRow == nextCell.row - doorCell.row && it.dCol == nextCell.col - doorCell.col
            } ?: Direction.DOWN

            val reachableBeforeDoor = findReachableCellsBeforeDoor(grid, start.row, start.col, doorCell.row, doorCell.col, doorDir)
            val keyCandidates = reachableBeforeDoor.filter { (r, c) ->
                !(r == start.row && c == start.col) &&
                    !(r == end.row && c == end.col) &&
                    !(r == doorCell.row && c == doorCell.col) &&
                    // never stack the key on a portal cell: both would trigger on the same landing
                    portal?.destinationFor(r, c) == null
            }

            if (keyCandidates.isNotEmpty()) {
                val chosenKey = keyCandidates[random.nextInt(keyCandidates.size)]
                keyDoor = KeyDoor(
                    keyRow = chosenKey.first,
                    keyCol = chosenKey.second,
                    doorRow = doorCell.row,
                    doorCol = doorCell.col,
                    doorDirection = doorDir
                )
            }
        }

        // 3. Hazards (patrol enemies along corridors).
        // Driven by the level's own hasHazards flag. Before, this was keyed on `hazards.isNotEmpty()`
        // which is always empty at this point, so hazard levels 21-25 had NO hazards while
        // key/portal-only levels randomly got some. Also guards the coerceIn range (crashed for
        // very short paths).
        if (baseMechanics.hasHazards && path.size >= 10) {
            val hazardCount = if (width * height > 400) 2 else 1
            for (h in 0 until hazardCount) {
                val startIdx = (path.size * (0.35f + h * 0.25f)).toInt().coerceIn(2, path.size - 5)
                val endIdx = (startIdx + 4).coerceAtMost(path.size - 1)
                val patrol = path.subList(startIdx, endIdx).map { Pair(it.row, it.col) }
                if (patrol.size >= 2) {
                    hazards.add(Hazard(id = h + 1, path = patrol, speed = 1.6f + h * 0.4f))
                }
            }
        }

        return baseMechanics.copy(
            keyDoor = keyDoor,
            portal = portal,
            hazards = hazards
        )
    }

    private fun findReachableCellsBeforeDoor(
        grid: List<List<Cell>>,
        startR: Int,
        startC: Int,
        doorR: Int,
        doorC: Int,
        doorDir: Direction
    ): List<Pair<Int, Int>> {
        val height = grid.size
        val width = grid[0].size
        val visited = Array(height) { BooleanArray(width) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        visited[startR][startC] = true
        queue.add(Pair(startR, startC))
        val result = mutableListOf<Pair<Int, Int>>()

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            result.add(Pair(r, c))

            val cell = grid[r][c]
            for (dir in Direction.entries) {
                if (!cell.hasWall(dir)) {
                    // Block passage across door
                    val isDoor = (r == doorR && c == doorC && dir == doorDir) ||
                                 (r == doorR + doorDir.dRow && c == doorC + doorDir.dCol && dir == doorDir.opposite)
                    if (!isDoor) {
                        val nr = r + dir.dRow
                        val nc = c + dir.dCol
                        if (nr in 0 until height && nc in 0 until width && !visited[nr][nc]) {
                            visited[nr][nc] = true
                            queue.add(Pair(nr, nc))
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * Generates a Recursive Backtracker maze with a low straightness bias to create long winding corridors.
     */
    private suspend fun generateWindingBacktracker(
        width: Int,
        height: Int,
        random: Random
    ): List<List<Cell>> {
        val cells = Array(height) { r ->
            Array(width) { c -> Cell(row = r, col = c, walls = 15) }
        }
        val visited = Array(height) { BooleanArray(width) }
        val stack = ArrayDeque<Pair<Int, Int>>()

        val startR = random.nextInt(height)
        val startC = random.nextInt(width)
        visited[startR][startC] = true
        stack.push(Pair(startR, startC))

        var prevDir: Direction? = null
        var loopCounter = 0

        while (stack.isNotEmpty()) {
            if (++loopCounter % 50 == 0) coroutineContext.ensureActive()

            val (cr, cc) = stack.first()
            val unvisited = mutableListOf<Pair<Direction, Pair<Int, Int>>>()

            for (dir in Direction.entries) {
                val nr = cr + dir.dRow
                val nc = cc + dir.dCol
                if (nr in 0 until height && nc in 0 until width && !visited[nr][nc]) {
                    unvisited.add(Pair(dir, Pair(nr, nc)))
                }
            }

            if (unvisited.isNotEmpty()) {
                // Low straightness bias: prefer changing directions 70% of the time to maximize winding corridors
                val chosen = if (prevDir != null && unvisited.any { it.first != prevDir } && random.nextFloat() < 0.70f) {
                    val turnOptions = unvisited.filter { it.first != prevDir }
                    turnOptions[random.nextInt(turnOptions.size)]
                } else {
                    unvisited[random.nextInt(unvisited.size)]
                }

                val (dir, next) = chosen
                cells[cr][cc] = cells[cr][cc].withWallRemoved(dir)
                cells[next.first][next.second] = cells[next.first][next.second].withWallRemoved(dir.opposite)

                visited[next.first][next.second] = true
                stack.push(next)
                prevDir = dir
            } else {
                stack.pop()
                prevDir = null
            }
        }

        return cells.map { it.toList() }
    }

    private suspend fun generatePrim(
        width: Int,
        height: Int,
        random: Random
    ): List<List<Cell>> {
        val cells = Array(height) { r ->
            Array(width) { c -> Cell(row = r, col = c, walls = 15) }
        }
        val inMaze = Array(height) { BooleanArray(width) }

        data class Wall(val r1: Int, val c1: Int, val dir: Direction, val r2: Int, val c2: Int)
        val frontierWalls = ArrayList<Wall>()

        fun addWalls(r: Int, c: Int) {
            for (dir in Direction.entries) {
                val nr = r + dir.dRow
                val nc = c + dir.dCol
                if (nr in 0 until height && nc in 0 until width && !inMaze[nr][nc]) {
                    frontierWalls.add(Wall(r, c, dir, nr, nc))
                }
            }
        }

        inMaze[0][0] = true
        addWalls(0, 0)

        var loopCounter = 0
        while (frontierWalls.isNotEmpty()) {
            if (++loopCounter % 50 == 0) coroutineContext.ensureActive()

            val randomIndex = random.nextInt(frontierWalls.size)
            val wall = frontierWalls[randomIndex]
            frontierWalls[randomIndex] = frontierWalls[frontierWalls.size - 1]
            frontierWalls.removeAt(frontierWalls.size - 1)

            if (!inMaze[wall.r2][wall.c2]) {
                inMaze[wall.r2][wall.c2] = true
                cells[wall.r1][wall.c1] = cells[wall.r1][wall.c1].withWallRemoved(wall.dir)
                cells[wall.r2][wall.c2] = cells[wall.r2][wall.c2].withWallRemoved(wall.dir.opposite)
                addWalls(wall.r2, wall.c2)
            }
        }

        return cells.map { it.toList() }
    }

    private suspend fun generateKruskal(
        width: Int,
        height: Int,
        random: Random
    ): List<List<Cell>> {
        val cells = Array(height) { r ->
            Array(width) { c -> Cell(row = r, col = c, walls = 15) }
        }
        val total = width * height
        val parent = IntArray(total) { it }
        val rank = IntArray(total) { 0 }

        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) root = parent[root]
            var curr = i
            while (curr != root) {
                val nxt = parent[curr]
                parent[curr] = root
                curr = nxt
            }
            return root
        }

        fun union(i: Int, j: Int): Boolean {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI == rootJ) return false
            if (rank[rootI] < rank[rootJ]) {
                parent[rootI] = rootJ
            } else if (rank[rootI] > rank[rootJ]) {
                parent[rootJ] = rootI
            } else {
                parent[rootJ] = rootI
                rank[rootI]++
            }
            return true
        }

        data class WallEdge(val r: Int, val c: Int, val dir: Direction)
        val allWalls = mutableListOf<WallEdge>()
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (r + 1 < height) allWalls.add(WallEdge(r, c, Direction.DOWN))
                if (c + 1 < width) allWalls.add(WallEdge(r, c, Direction.RIGHT))
            }
        }

        for (i in allWalls.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = allWalls[i]
            allWalls[i] = allWalls[j]
            allWalls[j] = temp
        }

        var loopCounter = 0
        for (wall in allWalls) {
            if (++loopCounter % 50 == 0) coroutineContext.ensureActive()

            val id1 = wall.r * width + wall.c
            val id2 = (wall.r + wall.dir.dRow) * width + (wall.c + wall.dir.dCol)

            if (union(id1, id2)) {
                cells[wall.r][wall.c] = cells[wall.r][wall.c].withWallRemoved(wall.dir)
                val nr = wall.r + wall.dir.dRow
                val nc = wall.c + wall.dir.dCol
                cells[nr][nc] = cells[nr][nc].withWallRemoved(wall.dir.opposite)
            }
        }

        return cells.map { it.toList() }
    }

    /**
     * Braid mode removes walls from a controlled percentage of dead ends to introduce cycles/loops.
     */
    private fun applyBraid(
        grid: List<List<Cell>>,
        width: Int,
        height: Int,
        random: Random,
        braidRatio: Float
    ): List<List<Cell>> {
        val cells = Array(height) { r -> Array(width) { c -> grid[r][c] } }

        for (r in 0 until height) {
            for (c in 0 until width) {
                val cell = cells[r][c]
                val openWalls = Direction.entries.count { !cell.hasWall(it) }
                if (openWalls == 1 && random.nextFloat() < braidRatio) {
                    val closedNeighbors = Direction.entries.filter { dir ->
                        val nr = r + dir.dRow
                        val nc = c + dir.dCol
                        nr in 0 until height && nc in 0 until width && cell.hasWall(dir)
                    }
                    if (closedNeighbors.isNotEmpty()) {
                        val chosen = closedNeighbors[random.nextInt(closedNeighbors.size)]
                        cells[r][c] = cells[r][c].withWallRemoved(chosen)
                        val nr = r + chosen.dRow
                        val nc = c + chosen.dCol
                        cells[nr][nc] = cells[nr][nc].withWallRemoved(chosen.opposite)
                    }
                }
            }
        }
        return cells.map { it.toList() }
    }

    private suspend fun generateFallback(
        levelNumber: Int,
        width: Int,
        height: Int,
        isDailyChallenge: Boolean,
        hasFogOfWar: Boolean
    ): Maze {
        val grid = generateWindingBacktracker(width, height, Random(levelNumber.toLong() + 101L))
        val start = grid[0][0]
        val end = grid[height - 1][width - 1]
        val solve = MazeSolver.solve(grid, 0, 0, height - 1, width - 1)
        val maze = Maze(
            levelNumber = levelNumber,
            width = width,
            height = height,
            grid = grid,
            start = start,
            end = end,
            optimalPath = solve.path,
            optimalMoves = solve.junctionMoves.coerceAtLeast(1),
            mechanics = LevelMechanics(hasFogOfWar = hasFogOfWar),
            isDailyChallenge = isDailyChallenge
        )
        val swipes = MazeSolver.solveSwipeMoves(maze)
        return if (swipes != null) maze.copy(optimalMoves = swipes.coerceAtLeast(1)) else maze
    }
}
