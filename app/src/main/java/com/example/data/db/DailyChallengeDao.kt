package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyChallengeDao {
    @Query("SELECT * FROM daily_challenge WHERE dateInt = :dateInt")
    fun getDailyChallenge(dateInt: Int): Flow<DailyChallengeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DailyChallengeEntity)

    @Query("SELECT * FROM daily_challenge ORDER BY dateInt DESC")
    fun getAllDailyChallenges(): Flow<List<DailyChallengeEntity>>

    @Query("DELETE FROM daily_challenge")
    suspend fun clearAll()
}
