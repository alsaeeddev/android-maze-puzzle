package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.RetroCyan
import com.example.ui.theme.RetroDarkSurface
import kotlinx.coroutines.delay

private val HudHeight = 48.dp
private val HudButtonSize = 44.dp
private const val TUTORIAL_AUTO_DISMISS_MS = 7000L

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The ViewModel also auto-pauses when the app goes to the background
                Lifecycle.Event.ON_PAUSE -> viewModel.onAction(GameUiAction.SetLifecycleActive(false))
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(GameUiAction.SetLifecycleActive(true))
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (!uiState.isPaused && !uiState.isCompleted) {
            viewModel.onAction(GameUiAction.Pause)
        } else {
            onNavigateBack()
        }
    }

    // luminance instead of "red channel < 0.5" (that only happened to work for the current palette)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Hazard clock for drawing. It is read only inside the canvas draw phase (via the lambda),
    // so 60fps updates redraw the canvas without recomposing the whole screen, and the loop only
    // runs on levels that actually have hazards.
    val hasHazards = uiState.maze?.mechanics?.hazards?.isNotEmpty() == true
    val hazardsRunning = hasHazards && !uiState.isPaused && !uiState.isCompleted && !uiState.isLoading
    var hazardTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hazardsRunning, uiState.maze) {
        if (hazardsRunning) {
            while (true) {
                withFrameNanos { hazardTime = viewModel.hazardTimeSeconds() }
            }
        } else {
            hazardTime = viewModel.hazardTimeSeconds()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            CompactGameHud(
                levelNumber = uiState.levelNumber,
                elapsedSeconds = uiState.elapsedSeconds,
                movesCount = uiState.movesCount,
                remainingHints = uiState.remainingHints,
                hasKey = uiState.hasKey,
                showKeyIndicator = uiState.maze?.mechanics?.keyDoor != null,
                isDaily = uiState.isDailyChallenge,
                onPause = { viewModel.onAction(GameUiAction.Pause) },
                onRestart = { viewModel.onAction(GameUiAction.Restart) },
                onHint = { viewModel.onAction(GameUiAction.RequestHint) }
            )

            // Maze container: uses all remaining space
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val widthDp = maxWidth.value
                val heightDp = maxHeight.value

                // The ViewModel picks the grid for the current level from this size
                LaunchedEffect(widthDp, heightDp) {
                    viewModel.onAction(GameUiAction.UpdateAvailableSize(widthDp, heightDp))
                }

                val maze = uiState.maze
                if (uiState.isLoading || maze == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(id = R.string.loading_maze),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MazeCanvas(
                        maze = maze,
                        ballRow = uiState.currentBallRow,
                        ballCol = uiState.currentBallCol,
                        ballScale = uiState.ballScale,
                        showHint = uiState.showHint,
                        hintPath = uiState.hintPath,
                        controlMode = uiState.controlMode,
                        isDarkTheme = isDarkTheme,
                        hasKey = uiState.hasKey,
                        exploredCells = uiState.exploredCells,
                        hazardTimeProvider = { hazardTime },
                        onSwipe = { dir -> viewModel.onAction(GameUiAction.Swipe(dir)) },
                        onDragStart = { r, c -> viewModel.onAction(GameUiAction.DragStart(r, c)) },
                        onDragUpdate = { r, c -> viewModel.onAction(GameUiAction.DragUpdate(r, c)) },
                        onDragEnd = { viewModel.onAction(GameUiAction.DragEnd) }
                    )
                }
            }
        }

        // Tutorial banner for newly unlocked mechanics (auto-hides, tap to dismiss earlier)
        val tutorial = uiState.activeTutorialMessage
        if (tutorial != null && !uiState.tutorialDismissed && !uiState.isLoading) {
            LaunchedEffect(tutorial) {
                delay(TUTORIAL_AUTO_DISMISS_MS)
                viewModel.onAction(GameUiAction.DismissTutorial)
            }
            TutorialBanner(
                message = tutorial,
                onDismiss = { viewModel.onAction(GameUiAction.DismissTutorial) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }

    // Dialogs
    if (uiState.isPaused) {
        PauseDialog(
            onResume = { viewModel.onAction(GameUiAction.Resume) },
            onRestart = { viewModel.onAction(GameUiAction.Restart) },
            onExit = onNavigateBack
        )
    }

    val score = uiState.completionScore
    if (uiState.isCompleted && score != null && uiState.maze != null) {
        WinDialog(
            levelNumber = uiState.levelNumber,
            isDaily = uiState.isDailyChallenge,
            score = score,
            elapsedSeconds = uiState.elapsedSeconds,
            movesTaken = uiState.movesCount,
            optimalMoves = uiState.completionOptimalMoves,
            onNextLevel = { viewModel.onAction(GameUiAction.NextLevel) },
            onReplay = { viewModel.onAction(GameUiAction.Restart) },
            onLevelSelect = onNavigateBack
        )
    }
}

@Composable
private fun CompactGameHud(
    levelNumber: Int,
    elapsedSeconds: Int,
    movesCount: Int,
    remainingHints: Int,
    hasKey: Boolean,
    showKeyIndicator: Boolean,
    isDaily: Boolean,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onHint: () -> Unit
) {
    // The HUD surface itself extends under the status bar. Before, the status bar inset was
    // applied to the whole screen, leaving a background-coloured strip above a differently
    // coloured HUD.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(HudHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Pause + Level tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPause,
                    modifier = Modifier
                        .size(HudButtonSize)
                        .testTag("game_pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = if (isDaily) "DAILY" else "Lv $levelNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            // Center: Timer & Moves
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$movesCount m",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )

                if (showKeyIndicator) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (hasKey) Icons.Filled.Key else Icons.Outlined.Key,
                        contentDescription = "Key",
                        tint = if (hasKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Right: Hint button & Restart
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onHint,
                    enabled = remainingHints > 0,
                    modifier = Modifier
                        .size(HudButtonSize)
                        .testTag("hint_button")
                ) {
                    Icon(
                        imageVector = if (remainingHints > 0) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                        contentDescription = "Hint ($remainingHints)",
                        tint = if (remainingHints > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .size(HudButtonSize)
                        .testTag("game_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Restart",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RetroDarkSurface,
        border = BorderStroke(1.dp, RetroCyan),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .testTag("tutorial_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = RetroCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
