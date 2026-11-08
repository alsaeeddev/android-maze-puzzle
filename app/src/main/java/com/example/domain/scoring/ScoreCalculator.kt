package com.example.domain.scoring

import com.example.domain.model.Difficulty

object ScoreCalculator {

    data class LevelScore(
        val stars: Int,
        val scorePoints: Int,
        val isThreeStars: Boolean
    )

    /**
     * Calculates star rating (1 to 3) based on actual moves and time taken
     * compared to the optimal multi-state BFS path and target time.
     * Higher difficulty tiers have stricter thresholds.
     */
    fun calculateStars(
        levelNumber: Int,
        movesTaken: Int,
        optimalMoves: Int,
        timeMillis: Long,
        gridArea: Int
    ): LevelScore {
        val safeOptimal = optimalMoves.coerceAtLeast(1)
        val targetTimeMillis = (gridArea * 400L).coerceAtLeast(3500L)

        val moveRatio = movesTaken.toDouble() / safeOptimal
        val timeRatio = timeMillis.toDouble() / targetTimeMillis

        val difficulty = Difficulty.forLevel(levelNumber)

        val (threeStarMoveRatio, threeStarTimeRatio) = when (difficulty) {
            Difficulty.EASY -> Pair(1.35, 1.60)
            Difficulty.MEDIUM -> Pair(1.25, 1.40)
            Difficulty.HARD -> Pair(1.18, 1.28)
            Difficulty.EXPERT -> Pair(1.12, 1.20)
        }

        val (twoStarMoveRatio, twoStarTimeRatio) = when (difficulty) {
            Difficulty.EASY -> Pair(2.20, 2.60)
            Difficulty.MEDIUM -> Pair(1.90, 2.20)
            Difficulty.HARD -> Pair(1.65, 1.90)
            Difficulty.EXPERT -> Pair(1.45, 1.65)
        }

        val stars = when {
            moveRatio <= threeStarMoveRatio && timeRatio <= threeStarTimeRatio -> 3
            moveRatio <= twoStarMoveRatio && timeRatio <= twoStarTimeRatio -> 2
            else -> 1
        }

        val basePoints = 1000
        val moveBonus = ((2.0 - moveRatio.coerceIn(1.0, 2.0)) * 500).toInt()
        val timeBonus = ((2.0 - timeRatio.coerceIn(1.0, 2.0)) * 500).toInt()
        val totalPoints = (basePoints + moveBonus + timeBonus).coerceAtLeast(100)

        return LevelScore(
            stars = stars,
            scorePoints = totalPoints,
            isThreeStars = (stars == 3)
        )
    }
}
