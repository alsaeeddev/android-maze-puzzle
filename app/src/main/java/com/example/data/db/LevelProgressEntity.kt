package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey
    val levelNumber: Int,
    val stars: Int,
    val bestTimeMillis: Long,
    val minMoves: Int,
    val completedAt: Long
)
