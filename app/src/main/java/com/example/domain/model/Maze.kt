package com.example.domain.model

data class Maze(
    val levelNumber: Int,
    val width: Int,
    val height: Int,
    val grid: List<List<Cell>>,
    val start: Cell,
    val end: Cell,
    val optimalPath: List<Cell>,
    val optimalMoves: Int,
    val mechanics: LevelMechanics = LevelMechanics(),
    val deadEndsCount: Int = 0,
    val junctionsCount: Int = 0,
    val isDailyChallenge: Boolean = false
) {
    fun getCell(row: Int, col: Int): Cell? {
        if (row !in 0 until height || col !in 0 until width) return null
        return grid[row][col]
    }
}
