package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_challenge")
data class DailyChallengeEntity(
    @PrimaryKey
    val dateInt: Int, // e.g. 20261106
    val completed: Boolean,
    val bestTimeMillis: Long,
    val minMoves: Int,
    val stars: Int,
    val completedAt: Long
)
