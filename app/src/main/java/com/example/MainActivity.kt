package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.ThemeMode
import com.example.ui.game.GameScreen
import com.example.ui.game.GameViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.howtoplay.HowToPlayScreen
import com.example.ui.levels.LevelSelectScreen
import com.example.ui.levels.LevelSelectViewModel
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.AsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as AsApplication).container

        setContent {
            val settings by appContainer.gameRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.preferences.GameSettings()
            )

            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Status/navigation bar icon colours must follow the APP theme. enableEdgeToEdge()
            // alone follows the system theme, so "Light" in-app on a dark phone gave white icons
            // on a light background (invisible clock/battery).
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark }
                )
                onDispose { }
            }

            AsTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AsNavGraph(appContainer = appContainer)
                }
            }
        }
    }
}

/** launchSingleTop: a double tap on Play/Levels no longer stacks two copies of the screen. */
private fun androidx.navigation.NavController.go(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable
fun AsNavGraph(
    appContainer: com.example.di.AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(appContainer.gameRepository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onPlayClick = { level ->
                    navController.go(Screen.Game.createRoute(level))
                },
                onLevelsClick = {
                    navController.go(Screen.LevelSelect.route)
                },
                onDailyChallengeClick = { dateInt ->
                    navController.go(Screen.DailyGame.createRoute(dateInt))
                },
                onSettingsClick = {
                    navController.go(Screen.Settings.route)
                },
                onHowToPlayClick = {
                    navController.go(Screen.HowToPlay.route)
                }
            )
        }

        composable(Screen.LevelSelect.route) {
            val levelSelectViewModel: LevelSelectViewModel = viewModel(
                factory = LevelSelectViewModel.provideFactory(appContainer.gameRepository)
            )
            LevelSelectScreen(
                viewModel = levelSelectViewModel,
                onLevelClick = { level ->
                    navController.go(Screen.Game.createRoute(level))
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument(Screen.Game.ARG_LEVEL_NUMBER) {
                    type = NavType.IntType
                    defaultValue = 1
                }
            )
        ) { backStackEntry ->
            val levelNumber = backStackEntry.arguments?.getInt(Screen.Game.ARG_LEVEL_NUMBER) ?: 1
            val gameViewModel: GameViewModel = viewModel(
                key = "game_vm_$levelNumber",
                factory = GameViewModel.provideFactory(
                    levelNumber = levelNumber,
                    isDailyChallenge = false,
                    dailyDateInt = 0,
                    repository = appContainer.gameRepository,
                    soundManager = appContainer.soundEffectManager,
                    hapticManager = appContainer.hapticFeedbackManager
                )
            )
            GameScreen(
                viewModel = gameViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.DailyGame.route,
            arguments = listOf(
                navArgument(Screen.DailyGame.ARG_DATE_INT) {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val dateInt = backStackEntry.arguments?.getInt(Screen.DailyGame.ARG_DATE_INT) ?: 0
            val gameViewModel: GameViewModel = viewModel(
                key = "daily_game_vm_$dateInt",
                factory = GameViewModel.provideFactory(
                    levelNumber = 25, // Large maze for daily challenge
                    isDailyChallenge = true,
                    dailyDateInt = dateInt,
                    repository = appContainer.gameRepository,
                    soundManager = appContainer.soundEffectManager,
                    hapticManager = appContainer.hapticFeedbackManager
                )
            )
            GameScreen(
                viewModel = gameViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(appContainer.gameRepository)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.HowToPlay.route) {
            HowToPlayScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
