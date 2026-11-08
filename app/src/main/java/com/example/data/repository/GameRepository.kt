package com.example.data.repository

import com.example.data.db.DailyChallengeDao
import com.example.data.db.DailyChallengeEntity
import com.example.data.db.LevelProgressDao
import com.example.data.db.LevelProgressEntity
import com.example.data.preferences.GamePreferencesRepository
import com.example.data.preferences.GameSettings
import com.example.domain.model.ControlMode
import com.example.domain.model.Maze
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface GameRepository {
    fun getLevelProgress(levelNumber: Int): Flow<LevelProgressEntity?>
    fun getAllProgress(): Flow<List<LevelProgressEntity>>
    fun getHighestUnlockedLevel(): Flow<Int>
    fun getTotalStars(): Flow<Int>
    suspend fun saveLevelResult(levelNumber: Int, stars: Int, timeMillis: Long, moves: Int)
    suspend fun resetAllProgress()

    // Daily Challenge
    fun getDailyChallenge(dateInt: Int): Flow<DailyChallengeEntity?>
    suspend fun saveDailyChallengeResult(dateInt: Int, stars: Int, timeMillis: Long, moves: Int)

    // Pre-generated maze cache
    fun cachePregeneratedMaze(maze: Maze)
    fun getCachedMaze(levelNumber: Int, width: Int, height: Int): Maze?

    val settingsFlow: Flow<GameSettings>
    suspend fun setControlMode(mode: ControlMode)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
}

class GameRepositoryImpl(
    private val levelProgressDao: LevelProgressDao,
    private val dailyChallengeDao: DailyChallengeDao,
    private val preferencesRepository: GamePreferencesRepository
) : GameRepository {

    @Volatile
    private var pregeneratedMaze: Maze? = null

    override fun getLevelProgress(levelNumber: Int): Flow<LevelProgressEntity?> {
        return levelProgressDao.getLevelProgress(levelNumber)
    }

    override fun getAllProgress(): Flow<List<LevelProgressEntity>> {
        return levelProgressDao.getAllProgress()
    }

    override fun getHighestUnlockedLevel(): Flow<Int> {
        return levelProgressDao.getHighestCompletedLevel().map { highest ->
            (highest ?: 0) + 1
        }
    }

    override fun getTotalStars(): Flow<Int> {
        return levelProgressDao.getTotalStars().map { it ?: 0 }
    }

    override suspend fun saveLevelResult(
        levelNumber: Int,
        stars: Int,
        timeMillis: Long,
        moves: Int
    ) {
        val existing = levelProgressDao.getLevelProgress(levelNumber).first()
        val bestStars = if (existing != null) maxOf(existing.stars, stars) else stars
        val bestTime = if (existing != null && existing.bestTimeMillis > 0) {
            minOf(existing.bestTimeMillis, timeMillis)
        } else {
            timeMillis
        }
        val bestMoves = if (existing != null && existing.minMoves > 0) {
            minOf(existing.minMoves, moves)
        } else {
            moves
        }

        val entity = LevelProgressEntity(
            levelNumber = levelNumber,
            stars = bestStars,
            bestTimeMillis = bestTime,
            minMoves = bestMoves,
            completedAt = System.currentTimeMillis()
        )
        levelProgressDao.insertOrUpdate(entity)
    }

    override fun getDailyChallenge(dateInt: Int): Flow<DailyChallengeEntity?> {
        return dailyChallengeDao.getDailyChallenge(dateInt)
    }

    override suspend fun saveDailyChallengeResult(
        dateInt: Int,
        stars: Int,
        timeMillis: Long,
        moves: Int
    ) {
        val existing = dailyChallengeDao.getDailyChallenge(dateInt).first()
        val bestTime = if (existing != null && existing.bestTimeMillis > 0) {
            minOf(existing.bestTimeMillis, timeMillis)
        } else {
            timeMillis
        }
        val bestMoves = if (existing != null && existing.minMoves > 0) {
            minOf(existing.minMoves, moves)
        } else {
            moves
        }
        val bestStars = if (existing != null) maxOf(existing.stars, stars) else stars

        val entity = DailyChallengeEntity(
            dateInt = dateInt,
            completed = true,
            bestTimeMillis = bestTime,
            minMoves = bestMoves,
            stars = bestStars,
            completedAt = System.currentTimeMillis()
        )
        dailyChallengeDao.insertOrUpdate(entity)
    }

    @Synchronized
    override fun cachePregeneratedMaze(maze: Maze) {
        pregeneratedMaze = maze
    }

    @Synchronized
    override fun getCachedMaze(levelNumber: Int, width: Int, height: Int): Maze? {
        val cached = pregeneratedMaze
        return if (cached != null && cached.levelNumber == levelNumber && cached.width == width && cached.height == height) {
            pregeneratedMaze = null
            cached
        } else {
            null
        }
    }

    override suspend fun resetAllProgress() {
        // Only game progress is wiped. Theme / sound / control settings are user
        // preferences, not progress, so they must survive a progress reset.
        levelProgressDao.clearAll()
        dailyChallengeDao.clearAll()
        synchronized(this) { pregeneratedMaze = null }
    }

    override val settingsFlow: Flow<GameSettings> = preferencesRepository.settingsFlow

    override suspend fun setControlMode(mode: ControlMode) {
        preferencesRepository.setControlMode(mode)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        preferencesRepository.setSoundEnabled(enabled)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        preferencesRepository.setHapticsEnabled(enabled)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }
}
