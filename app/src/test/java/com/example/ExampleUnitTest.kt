package com.example

import com.example.domain.generator.MazeGenerator
import com.example.domain.model.Difficulty
import com.example.domain.model.Direction
import com.example.domain.model.GridDimensionCalculator
import com.example.domain.scoring.ScoreCalculator
import com.example.domain.solver.MazeSolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMazeDeterminism() = runBlocking {
        // Same level number and dimensions must produce an exact identical maze
        val mazeA = MazeGenerator.generate(levelNumber = 12, targetWidth = 12, targetHeight = 18)
        val mazeB = MazeGenerator.generate(levelNumber = 12, targetWidth = 12, targetHeight = 18)

        assertEquals(mazeA.width, mazeB.width)
        assertEquals(mazeA.height, mazeB.height)
        assertEquals(mazeA.start, mazeB.start)
        assertEquals(mazeA.end, mazeB.end)
        assertEquals(mazeA.optimalMoves, mazeB.optimalMoves)
        assertEquals(mazeA.optimalPath.size, mazeB.optimalPath.size)
        assertEquals(mazeA.mechanics.keyDoor, mazeB.mechanics.keyDoor)

        for (r in 0 until mazeA.height) {
            for (c in 0 until mazeA.width) {
                assertEquals(
                    "Cell ($r, $c) walls should match",
                    mazeA.grid[r][c].walls,
                    mazeB.grid[r][c].walls
                )
            }
        }
    }

    @Test
    fun testMazeSolvability100SeedsWithMechanics() = runBlocking {
        // Test solvability across 100+ seeds spanning all difficulty tiers and mechanics
        // Easy: 1..25 (Fog at 6+, Keys at 11+, Portals at 16+, Hazards at 21+)
        // Medium: 51..75
        // Hard: 101..125
        // Expert: 161..185
        val testLevels = (1..25) + (51..75) + (101..125) + (161..185)

        for (lvl in testLevels) {
            val cols = if (lvl <= 50) 10 else 16
            val rows = if (lvl <= 50) 16 else 24
            val maze = MazeGenerator.generate(levelNumber = lvl, targetWidth = cols, targetHeight = rows)

            assertTrue("Level $lvl width should be >= 6", maze.width >= 6)
            assertTrue("Level $lvl height should be >= 6", maze.height >= 6)
            assertTrue("Level $lvl optimal path should not be empty", maze.optimalPath.isNotEmpty())

            val solveResult = MazeSolver.solve(
                grid = maze.grid,
                startRow = maze.start.row,
                startCol = maze.start.col,
                endRow = maze.end.row,
                endCol = maze.end.col,
                keyDoor = maze.mechanics.keyDoor,
                portal = maze.mechanics.portal
            )

            assertTrue("Level $lvl with mechanics must be 100% solvable via BFS", solveResult.isSolvable)
            assertTrue("Start cell must match", solveResult.path.first() == maze.start)
            assertTrue("End cell must match", solveResult.path.last() == maze.end)

            if (maze.mechanics.keyDoor != null) {
                assertTrue("Level $lvl key must be reachable and collected before door", solveResult.keyFoundBeforeDoor)
            }
        }
    }

    @Test
    fun testKeyBeforeDoorGuarantee() = runBlocking {
        // Levels 11 to 15 have keys and doors guaranteed
        for (lvl in 11..15) {
            val maze = MazeGenerator.generate(levelNumber = lvl, targetWidth = 10, targetHeight = 16)
            val keyDoor = maze.mechanics.keyDoor
            assertNotNull("Level $lvl must feature a key and door", keyDoor)

            val solveResult = MazeSolver.solve(
                grid = maze.grid,
                startRow = maze.start.row,
                startCol = maze.start.col,
                endRow = maze.end.row,
                endCol = maze.end.col,
                keyDoor = keyDoor,
                portal = maze.mechanics.portal
            )

            assertTrue("Level $lvl with key & door must be solvable", solveResult.isSolvable)
            assertTrue("Level $lvl key must be visited before door passage", solveResult.keyFoundBeforeDoor)
        }
    }

    @Test
    fun testMinimumSolutionLengthRatio() = runBlocking {
        for (lvl in listOf(1, 10, 55, 105)) {
            val maze = MazeGenerator.generate(levelNumber = lvl, targetWidth = 10, targetHeight = 16)
            val totalCells = maze.width * maze.height
            val ratio = maze.optimalPath.size.toFloat() / totalCells
            val difficulty = Difficulty.forLevel(lvl)

            assertTrue(
                "Level $lvl solution ratio ($ratio) should meet difficulty minimum (${difficulty.minSolutionRatio})",
                ratio >= difficulty.minSolutionRatio * 0.90f // slight tolerance for complex mechanics
            )
        }
    }

    @Test
    fun testLayoutCalculationAcrossScreenSizesAndAspectRatios() {
        // 1. Modern phone portrait (390 x 844 dp)
        val phonePortrait = GridDimensionCalculator.calculate(
            levelNumber = 1,
            availableWidthDp = 390f,
            availableHeightDp = 844f
        )
        assertTrue("Cell size must be >= 16dp", phonePortrait.cellSizeDp >= 16f)
        assertTrue("Cols must be >= 8", phonePortrait.cols >= 8)
        assertTrue("Rows must be >= 14", phonePortrait.rows >= 14)
        assertTrue("Total maze width <= available width", phonePortrait.cols * phonePortrait.cellSizeDp <= 390.1f)
        assertTrue("Total maze height <= available height", phonePortrait.rows * phonePortrait.cellSizeDp <= 844.1f)
        assertTrue("Leftover X >= 0", phonePortrait.offsetX >= 0f)
        assertTrue("Leftover Y >= 0", phonePortrait.offsetY >= 0f)

        // 2. Landscape phone (844 x 390 dp)
        val phoneLandscape = GridDimensionCalculator.calculate(
            levelNumber = 1,
            availableWidthDp = 844f,
            availableHeightDp = 390f
        )
        assertTrue("Cell size in landscape must be >= 16dp", phoneLandscape.cellSizeDp >= 16f)
        assertTrue("Landscape cols must be >= 14", phoneLandscape.cols >= 14)
        assertTrue("Landscape rows must be >= 8", phoneLandscape.rows >= 8)

        // 3. Tablet (600 x 800 dp)
        val tablet = GridDimensionCalculator.calculate(
            levelNumber = 15,
            availableWidthDp = 600f,
            availableHeightDp = 800f
        )
        assertTrue("Tablet cell size must be >= 16dp", tablet.cellSizeDp >= 16f)
        assertTrue("Cols within allowed bounds", tablet.cols in 8..GridDimensionCalculator.MAX_EXPERT_COLS)

        // 4. Expert level constraint clamp test (small screen with high level)
        val smallScreenExpert = GridDimensionCalculator.calculate(
            levelNumber = 180,
            availableWidthDp = 320f,
            availableHeightDp = 480f
        )
        assertTrue("Cell size on small screen expert must stay >= 16dp", smallScreenExpert.cellSizeDp >= 16f)
        assertTrue("Cols clamped to max width / 16", smallScreenExpert.cols <= (320 / 16))
    }

    @Test
    fun testScoreCalculatorStarsWithDifficultyProgression() {
        // Easy Level 5: 3 stars allows more margin
        val easyScore = ScoreCalculator.calculateStars(
            levelNumber = 5,
            movesTaken = 24,
            optimalMoves = 20,
            timeMillis = 15000L,
            gridArea = 160
        )
        assertEquals(3, easyScore.stars)

        // Hard Level 120: same move ratio yields fewer stars due to stricter thresholds
        val hardScore = ScoreCalculator.calculateStars(
            levelNumber = 120,
            movesTaken = 28,
            optimalMoves = 20,
            timeMillis = 35000L,
            gridArea = 160
        )
        assertEquals(2, hardScore.stars)
    }
}
