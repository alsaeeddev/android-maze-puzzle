package com.example.ui.game

import androidx.compose.runtime.Immutable
import com.example.domain.model.Cell
import com.example.domain.model.ControlMode
import com.example.domain.model.Difficulty
import com.example.domain.model.GridDimensions
import com.example.domain.model.Maze
import com.example.domain.scoring.ScoreCalculator

@Immutable
data class GameUiState(
    val isLoading: Boolean = true,
    val levelNumber: Int = 1,
    val isDailyChallenge: Boolean = false,
    val dailyDateInt: Int = 0,
    val maze: Maze? = null,
    val gridDimensions: GridDimensions? = null,
    val difficulty: Difficulty = Difficulty.EASY,
    val currentBallRow: Float = 0f,
    val currentBallCol: Float = 0f,
    val ballScale: Float = 1f,
    val isAnimatingMove: Boolean = false,
    val movesCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val completionScore: ScoreCalculator.LevelScore? = null,
    /** The "optimal" move count the score was measured against (depends on the control mode). */
    val completionOptimalMoves: Int = 0,
    val remainingHints: Int = 3,
    val showHint: Boolean = false,
    val hintPath: List<Cell> = emptyList(), // next ~10 cells only
    val controlMode: ControlMode = ControlMode.SWIPE,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,

    // Mechanic runtime states
    /** Fog-of-war memory. Each cell is encoded as `row * maze.width + col` (no Pair allocations). */
    val exploredCells: Set<Int> = emptySet(),
    val hasKey: Boolean = false,
    val activeTutorialMessage: String? = null,
    val tutorialDismissed: Boolean = false
)
