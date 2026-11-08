package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object LevelSelect : Screen("level_select")
    data object Settings : Screen("settings")
    data object HowToPlay : Screen("how_to_play")
    data object Game : Screen("game/{levelNumber}") {
        const val ARG_LEVEL_NUMBER = "levelNumber"
        fun createRoute(levelNumber: Int): String = "game/$levelNumber"
    }
    data object DailyGame : Screen("daily_game/{dateInt}") {
        const val ARG_DATE_INT = "dateInt"
        fun createRoute(dateInt: Int): String = "daily_game/$dateInt"
    }
}
