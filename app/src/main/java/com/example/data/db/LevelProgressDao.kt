package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelProgressDao {

    @Query("SELECT * FROM level_progress WHERE levelNumber = :levelNumber")
    fun getLevelProgress(levelNumber: Int): Flow<LevelProgressEntity?>

    @Query("SELECT * FROM level_progress ORDER BY levelNumber ASC")
    fun getAllProgress(): Flow<List<LevelProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: LevelProgressEntity)

    @Query("SELECT MAX(levelNumber) FROM level_progress")
    fun getHighestCompletedLevel(): Flow<Int?>

    @Query("SELECT SUM(stars) FROM level_progress")
    fun getTotalStars(): Flow<Int?>

    @Query("DELETE FROM level_progress")
    suspend fun clearAll()
}
