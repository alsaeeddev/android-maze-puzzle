package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DailyChallengeDao
import com.example.data.db.DailyChallengeEntity
import com.example.data.db.LevelProgressDao
import com.example.data.db.LevelProgressEntity
import com.example.data.db.MazeDatabase
import com.example.domain.model.Cell
import com.example.domain.model.ControlMode
import com.example.domain.model.Direction
import com.example.domain.model.Hazard
import com.example.domain.model.KeyDoor
import com.example.domain.model.LevelMechanics
import com.example.domain.model.Maze
import com.example.domain.model.Portal
import com.example.domain.model.ThemeMode
import com.example.ui.game.MazeCanvas
import com.example.ui.theme.AsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: MazeDatabase
    private lateinit var levelDao: LevelProgressDao
    private lateinit var dailyDao: DailyChallengeDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MazeDatabase::class.java)
            .addMigrations(MazeDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        levelDao = database.levelProgressDao()
        dailyDao = database.dailyChallengeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testAppNameStringResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("As", appName)
    }

    @Test
    fun testRoomLevelProgressPersistence() = runBlocking {
        val entity = LevelProgressEntity(
            levelNumber = 1,
            stars = 3,
            bestTimeMillis = 12000L,
            minMoves = 15,
            completedAt = System.currentTimeMillis()
        )
        levelDao.insertOrUpdate(entity)

        val retrieved = levelDao.getLevelProgress(1).first()
        assertNotNull(retrieved)
        assertEquals(3, retrieved?.stars)
        assertEquals(12000L, retrieved?.bestTimeMillis)
        assertEquals(15, retrieved?.minMoves)
    }

    @Test
    fun testDailyChallengeRoomPersistence() = runBlocking {
        val today = 20261106
        val daily = DailyChallengeEntity(
            dateInt = today,
            completed = true,
            bestTimeMillis = 45000L,
            minMoves = 38,
            stars = 3,
            completedAt = System.currentTimeMillis()
        )
        dailyDao.insertOrUpdate(daily)

        val retrieved = dailyDao.getDailyChallenge(today).first()
        assertNotNull(retrieved)
        assertTrue(retrieved?.completed == true)
        assertEquals(45000L, retrieved?.bestTimeMillis)
        assertEquals(38, retrieved?.minMoves)
        assertEquals(3, retrieved?.stars)
    }

    @Test
    fun testMazeCanvasRenderingWithAllMechanics() {
        val cell1 = Cell(0, 0, walls = 9)
        val cell2 = Cell(0, 1, walls = 3)
        val cell3 = Cell(1, 0, walls = 12)
        val cell4 = Cell(1, 1, walls = 6)
        val maze = Maze(
            levelNumber = 16,
            width = 2,
            height = 2,
            grid = listOf(listOf(cell1, cell2), listOf(cell3, cell4)),
            start = cell1,
            end = cell4,
            optimalPath = listOf(cell1, cell2, cell4),
            optimalMoves = 2,
            mechanics = LevelMechanics(
                hasFogOfWar = true,
                keyDoor = KeyDoor(0, 1, 1, 0, Direction.DOWN),
                portal = Portal(1, 0, 0, 1, 1),
                hazards = listOf(Hazard(1, listOf(Pair(0, 0), Pair(0, 1))))
            )
        )

        composeTestRule.setContent {
            AsTheme {
                MazeCanvas(
                    maze = maze,
                    ballRow = 0f,
                    ballCol = 0f,
                    ballScale = 1f,
                    showHint = true,
                    hintPath = listOf(cell1, cell2),
                    controlMode = ControlMode.SWIPE,
                    isDarkTheme = true,
                    hasKey = false,
                    exploredCells = setOf(Pair(0, 0)),
                    elapsedSeconds = 1.0f,
                    onSwipe = {},
                    onDrag = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
    }

    @Test
    fun testMazeCanvasRenderingInLightTheme() {
        val cell1 = Cell(0, 0, walls = 9)
        val cell2 = Cell(0, 1, walls = 3)
        val cell3 = Cell(1, 0, walls = 12)
        val cell4 = Cell(1, 1, walls = 6)
        val maze = Maze(
            levelNumber = 1,
            width = 2,
            height = 2,
            grid = listOf(listOf(cell1, cell2), listOf(cell3, cell4)),
            start = cell1,
            end = cell4,
            optimalPath = listOf(cell1, cell2, cell4),
            optimalMoves = 2
        )

        composeTestRule.setContent {
            AsTheme(themeMode = ThemeMode.LIGHT) {
                MazeCanvas(
                    maze = maze,
                    ballRow = 0f,
                    ballCol = 0f,
                    ballScale = 1f,
                    showHint = false,
                    hintPath = emptyList(),
                    controlMode = ControlMode.DRAG,
                    isDarkTheme = false,
                    hasKey = false,
                    exploredCells = emptySet(),
                    elapsedSeconds = 0f,
                    onSwipe = {},
                    onDragStart = { _, _ -> },
                    onDragUpdate = { _, _ -> },
                    onDragEnd = {}
                )
            }
        }

        composeTestRule.waitForIdle()
    }
}
