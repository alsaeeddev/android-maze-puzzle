package com.example.ui.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.haptics.HapticFeedbackManager
import com.example.data.repository.GameRepository
import com.example.data.sound.SoundEffectManager
import com.example.domain.generator.MazeGenerator
import com.example.domain.model.ControlMode
import com.example.domain.model.Difficulty
import com.example.domain.model.Direction
import com.example.domain.model.GridDimensionCalculator
import com.example.domain.model.GridDimensions
import com.example.domain.model.Maze
import com.example.domain.scoring.ScoreCalculator
import com.example.domain.solver.MazeSolver
import com.example.domain.solver.SlideSimulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class GameViewModel(
    private val initialLevel: Int,
    private val isDailyChallenge: Boolean = false,
    private val dailyDateInt: Int = 0,
    private val repository: GameRepository,
    private val soundManager: SoundEffectManager,
    private val hapticManager: HapticFeedbackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GameUiState(
            levelNumber = initialLevel,
            isDailyChallenge = isDailyChallenge,
            dailyDateInt = dailyDateInt,
            difficulty = if (isDailyChallenge) Difficulty.HARD else Difficulty.forLevel(initialLevel),
            remainingHints = (if (isDailyChallenge) Difficulty.HARD else Difficulty.forLevel(initialLevel)).initialHints
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var hazardJob: Job? = null
    private var moveAnimationJob: Job? = null
    private var hintJob: Job? = null
    private var loadJob: Job? = null
    private var pregenJob: Job? = null

    @Volatile private var isLifecycleActive: Boolean = true
    @Volatile private var completionStarted: Boolean = false

    private var availableWidthDp = 0f
    private var availableHeightDp = 0f
    private var currentGridDimensions: GridDimensions? = null

    /** One swipe that arrived while the ball was still sliding; replayed when the slide ends. */
    private var bufferedDirection: Direction? = null
    private var lastBumpMs = 0L

    /**
     * Single clock shared by hazard collision (here) and hazard drawing (UI via [hazardTimeSeconds]).
     * Previously the UI drew hazards from its own frame timer while collisions used a different
     * clock, so you could die to a hazard that was drawn somewhere else.
     */
    private class HazardClock {
        private var accumMs = 0L
        private var lastMs = 0L

        @Synchronized
        fun reset() {
            accumMs = 0L
            lastMs = 0L
        }

        @Synchronized
        fun update(running: Boolean, nowMs: Long) {
            if (lastMs != 0L) accumMs += nowMs - lastMs
            lastMs = if (running) nowMs else 0L
        }

        @Synchronized
        fun seconds(nowMs: Long): Float =
            (accumMs + if (lastMs != 0L) nowMs - lastMs else 0L) / 1000f
    }

    private val hazardClock = HazardClock()

    fun hazardTimeSeconds(): Float = hazardClock.seconds(SystemClock.elapsedRealtime())

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                soundManager.isSoundEnabled = settings.soundEnabled
                hapticManager.isHapticsEnabled = settings.hapticsEnabled
                _uiState.update {
                    it.copy(
                        controlMode = settings.controlMode,
                        soundEnabled = settings.soundEnabled,
                        hapticsEnabled = settings.hapticsEnabled
                    )
                }
            }
        }
    }

    fun onAction(action: GameUiAction) {
        when (action) {
            is GameUiAction.Swipe -> handleSwipe(action.direction)
            is GameUiAction.DragStart -> handleDragStart(action.touchRow, action.touchCol)
            is GameUiAction.DragUpdate -> handleDragUpdate(action.touchRow, action.touchCol)
            GameUiAction.DragEnd -> handleDragEnd()
            GameUiAction.RequestHint -> requestHint()
            GameUiAction.Pause -> pauseGame()
            GameUiAction.Resume -> resumeGame()
            GameUiAction.Restart -> loadLevel(_uiState.value.levelNumber)
            GameUiAction.NextLevel -> if (!isDailyChallenge) loadLevel(_uiState.value.levelNumber + 1)
            is GameUiAction.SetLifecycleActive -> setLifecycleActive(action.isActive)
            GameUiAction.DismissTutorial -> dismissTutorial()
            is GameUiAction.UpdateAvailableSize -> handleAvailableSize(action.widthDp, action.heightDp)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Level loading
    // ---------------------------------------------------------------------------------------

    private fun handleAvailableSize(widthDp: Float, heightDp: Float) {
        if (widthDp <= 0f || heightDp <= 0f) return
        availableWidthDp = widthDp
        availableHeightDp = heightDp

        val state = _uiState.value
        val dims = calculateDimensions(state.levelNumber)
        val current = currentGridDimensions

        // Same grid, only a few pixels of difference: just remember it, don't throw the run away.
        if (current != null && current.cols == dims.cols && current.rows == dims.rows &&
            (state.maze != null || state.isLoading)
        ) {
            currentGridDimensions = dims
            _uiState.update { it.copy(gridDimensions = dims) }
            return
        }
        startLoad(state.levelNumber, dims)
    }

    private fun calculateDimensions(levelNumber: Int): GridDimensions =
        GridDimensionCalculator.calculate(
            levelNumber = levelNumber,
            availableWidthDp = availableWidthDp,
            availableHeightDp = availableHeightDp,
            isDailyChallenge = isDailyChallenge
        )

    /** Loads (or restarts) a level using the current screen size. */
    private fun loadLevel(levelNumber: Int) {
        if (availableWidthDp <= 0f || availableHeightDp <= 0f) {
            // Screen not measured yet; the load starts as soon as UpdateAvailableSize arrives.
            _uiState.update { it.copy(levelNumber = levelNumber) }
            return
        }
        startLoad(levelNumber, calculateDimensions(levelNumber))
    }

    private fun startLoad(levelNumber: Int, dims: GridDimensions) {
        timerJob?.cancel()
        hazardJob?.cancel()
        moveAnimationJob?.cancel()
        hintJob?.cancel()
        loadJob?.cancel()
        pregenJob?.cancel()

        completionStarted = false
        bufferedDirection = null
        resetDragState()
        hazardClock.reset()
        currentGridDimensions = dims

        val difficulty = if (isDailyChallenge) Difficulty.HARD else Difficulty.forLevel(levelNumber)

        _uiState.update {
            it.copy(
                isLoading = true,
                levelNumber = levelNumber,
                difficulty = difficulty,
                gridDimensions = dims,
                maze = null,
                isPaused = false,
                isCompleted = false,
                completionScore = null,
                completionOptimalMoves = 0,
                showHint = false,
                hintPath = emptyList(),
                ballScale = 1f,
                isAnimatingMove = false,
                remainingHints = difficulty.initialHints,
                movesCount = 0,
                elapsedSeconds = 0,
                exploredCells = emptySet(),
                hasKey = false,
                activeTutorialMessage = if (isDailyChallenge) null else getTutorialForLevel(levelNumber),
                tutorialDismissed = false
            )
        }

        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val cached = if (isDailyChallenge) null else repository.getCachedMaze(levelNumber, dims.cols, dims.rows)
            val maze = cached ?: MazeGenerator.generate(
                levelNumber = levelNumber,
                targetWidth = dims.cols,
                targetHeight = dims.rows,
                isDailyChallenge = isDailyChallenge,
                dailySeed = dailyDateInt
            )
            ensureActive()

            val initialExplored = computeExploredCells(maze, maze.start.row, maze.start.col, emptySet())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    maze = maze,
                    currentBallRow = maze.start.row.toFloat(),
                    currentBallCol = maze.start.col.toFloat(),
                    exploredCells = initialExplored,
                    hasKey = false
                )
            }

            hazardClock.reset()
            startTimer()
            startHazardLoop()

            // Pre-generate next level in the background
            if (!isDailyChallenge) pregenerateNextLevel(levelNumber + 1)
        }
    }

    private fun pregenerateNextLevel(nextLevel: Int) {
        pregenJob?.cancel()
        // The next level usually has a different grid size, so use the size it will really get.
        val dims = calculateDimensions(nextLevel)
        pregenJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val nextMaze = MazeGenerator.generate(
                    levelNumber = nextLevel,
                    targetWidth = dims.cols,
                    targetHeight = dims.rows,
                    isDailyChallenge = false
                )
                repository.cachePregeneratedMaze(nextMaze)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Pre-generation is only an optimisation
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Timer & hazards
    // ---------------------------------------------------------------------------------------

    private fun isRunning(state: GameUiState): Boolean =
        !state.isPaused && !state.isCompleted && !state.isLoading && isLifecycleActive && !completionStarted

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (isRunning(_uiState.value)) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    private fun startHazardLoop() {
        hazardJob?.cancel()
        hazardJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(HAZARD_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val state = _uiState.value
                val maze = state.maze
                val running = maze != null && isRunning(state)
                hazardClock.update(running, now)

                if (maze == null || !running || maze.mechanics.hazards.isEmpty()) continue

                val elapsed = hazardClock.seconds(now)
                for (hazard in maze.mechanics.hazards) {
                    val (hr, hc) = hazard.positionAt(elapsed)
                    if (hypot(state.currentBallRow - hr, state.currentBallCol - hc) < HAZARD_HIT_RADIUS) {
                        onHazardCollision(maze)
                        break
                    }
                }
            }
        }
    }

    private fun onHazardCollision(maze: Maze) {
        // Stop any slide in progress, otherwise it would keep moving the ball from the old spot
        moveAnimationJob?.cancel()
        bufferedDirection = null
        resetDragState()
        bump()
        _uiState.update {
            it.copy(
                currentBallRow = maze.start.row.toFloat(),
                currentBallCol = maze.start.col.toFloat(),
                isAnimatingMove = false
            )
        }
    }

    /** Wall feedback, throttled so dragging against a wall doesn't machine-gun sound + vibration. */
    private fun bump() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBumpMs < BUMP_COOLDOWN_MS) return
        lastBumpMs = now
        soundManager.playBump()
        hapticManager.bumpWall()
    }

    // ---------------------------------------------------------------------------------------
    // SWIPE mode
    // ---------------------------------------------------------------------------------------

    private fun canPlay(state: GameUiState): Boolean =
        !state.isLoading && !state.isPaused && !state.isCompleted && !completionStarted

    private fun handleSwipe(direction: Direction) {
        val state = _uiState.value
        val maze = state.maze ?: return
        if (!canPlay(state)) return

        if (state.isAnimatingMove) {
            // Keep the latest swipe instead of dropping it - fast players chain swipes.
            bufferedDirection = direction
            return
        }
        bufferedDirection = null

        val startR = state.currentBallRow.roundToInt().coerceIn(0, maze.height - 1)
        val startC = state.currentBallCol.roundToInt().coerceIn(0, maze.width - 1)

        // Snap to exact integer coordinates before moving so the ball is never offset
        _uiState.update { it.copy(currentBallRow = startR.toFloat(), currentBallCol = startC.toFloat()) }

        val steps = SlideSimulator.computeSlide(maze, startR, startC, direction, state.hasKey)
        if (steps.isEmpty()) {
            bump()
            return
        }
        animateSlide(maze, steps, direction, state.hasKey)
    }

    private fun animateSlide(
        maze: Maze,
        steps: List<Pair<Int, Int>>,
        direction: Direction,
        hasKeyAtStart: Boolean
    ) {
        moveAnimationJob?.cancel()
        _uiState.update {
            it.copy(
                isAnimatingMove = true,
                movesCount = it.movesCount + 1
            )
        }

        soundManager.playMove()

        moveAnimationJob = viewModelScope.launch(Dispatchers.Default) {
            var currentR = _uiState.value.currentBallRow.roundToInt()
            var currentC = _uiState.value.currentBallCol.roundToInt()

            for (step in steps) {
                val targetR = step.first
                val targetC = step.second

                for (s in 1..SLIDE_SUBSTEPS) {
                    val progress = s.toFloat() / SLIDE_SUBSTEPS
                    // Lock the perpendicular axis to the corridor line so the ball never touches walls
                    val r = if (targetR == currentR) currentR.toFloat() else currentR + (targetR - currentR) * progress
                    val c = if (targetC == currentC) currentC.toFloat() else currentC + (targetC - currentC) * progress

                    val explored = if (maze.mechanics.hasFogOfWar) {
                        computeExploredCells(maze, r.roundToInt(), c.roundToInt(), _uiState.value.exploredCells)
                    } else {
                        _uiState.value.exploredCells
                    }

                    _uiState.update {
                        it.copy(currentBallRow = r, currentBallCol = c, exploredCells = explored)
                    }
                    delay(16L)
                }
                currentR = targetR
                currentC = targetC
            }

            val last = steps.last()
            val landing = SlideSimulator.resolveLanding(maze, last.first, last.second, _uiState.value.hasKey)

            if (landing.pickedUpKey) {
                soundManager.playHint()
                hapticManager.victory()
            }
            if (landing.teleported) {
                soundManager.playHint()
                hapticManager.hint()
            }

            val explored = if (maze.mechanics.hasFogOfWar && landing.teleported) {
                computeExploredCells(maze, landing.row, landing.col, _uiState.value.exploredCells)
            } else {
                _uiState.value.exploredCells
            }

            _uiState.update {
                it.copy(
                    currentBallRow = landing.row.toFloat(),
                    currentBallCol = landing.col.toFloat(),
                    isAnimatingMove = false,
                    hasKey = landing.hasKey,
                    exploredCells = explored
                )
            }

            if (landing.row == maze.end.row && landing.col == maze.end.col) {
                onLevelCompleted()
                return@launch
            }

            // Only a real wall hit feels like a bump; stopping at a junction / key / portal does not.
            val lastCell = maze.getCell(last.first, last.second)
            val hitWall = lastCell != null && !landing.pickedUpKey && !landing.teleported &&
                SlideSimulator.isBlocked(lastCell, direction, hasKeyAtStart, maze.mechanics.keyDoor)
            if (hitWall) bump()

            val next = bufferedDirection
            if (next != null) {
                bufferedDirection = null
                handleSwipe(next)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // DRAG mode
    // ---------------------------------------------------------------------------------------

    private var dragAccumulatedR = 0f
    private var dragAccumulatedC = 0f
    private var lastTouchR = 0f
    private var lastTouchC = 0f
    private var isDraggingActive = false

    private fun resetDragState() {
        isDraggingActive = false
        dragAccumulatedR = 0f
        dragAccumulatedC = 0f
    }

    private fun handleDragStart(touchRow: Float, touchCol: Float) {
        val state = _uiState.value
        if (!canPlay(state)) return

        moveAnimationJob?.cancel()
        val snapR = state.currentBallRow.roundToInt().toFloat()
        val snapC = state.currentBallCol.roundToInt().toFloat()
        _uiState.update { it.copy(currentBallRow = snapR, currentBallCol = snapC, isAnimatingMove = false) }

        isDraggingActive = true
        lastTouchR = touchRow
        lastTouchC = touchCol
        dragAccumulatedR = 0f
        dragAccumulatedC = 0f
    }

    private fun handleDragUpdate(touchRow: Float, touchCol: Float) {
        val state = _uiState.value
        val maze = state.maze ?: return
        if (!canPlay(state)) return

        if (!isDraggingActive) {
            handleDragStart(touchRow, touchCol)
            return
        }

        dragAccumulatedR += touchRow - lastTouchR
        dragAccumulatedC += touchCol - lastTouchC
        lastTouchR = touchRow
        lastTouchC = touchCol

        val keyDoor = maze.mechanics.keyDoor
        var currR = state.currentBallRow.roundToInt().coerceIn(0, maze.height - 1)
        var currC = state.currentBallCol.roundToInt().coerceIn(0, maze.width - 1)
        var hasKey = state.hasKey
        var movesAdded = 0

        var maxSteps = 4
        while (maxSteps-- > 0 &&
            (abs(dragAccumulatedC) >= DRAG_STEP_THRESHOLD || abs(dragAccumulatedR) >= DRAG_STEP_THRESHOLD)
        ) {
            val cell = maze.getCell(currR, currC) ?: break
            val horizontalFirst = abs(dragAccumulatedC) >= abs(dragAccumulatedR)

            val primary = if (horizontalFirst) {
                if (dragAccumulatedC > 0f) Direction.RIGHT else Direction.LEFT
            } else {
                if (dragAccumulatedR > 0f) Direction.DOWN else Direction.UP
            }
            val secondary: Direction? = if (horizontalFirst) {
                if (abs(dragAccumulatedR) >= DRAG_STEP_THRESHOLD) {
                    if (dragAccumulatedR > 0f) Direction.DOWN else Direction.UP
                } else null
            } else {
                if (abs(dragAccumulatedC) >= DRAG_STEP_THRESHOLD) {
                    if (dragAccumulatedC > 0f) Direction.RIGHT else Direction.LEFT
                } else null
            }

            val moveDir: Direction
            if (!SlideSimulator.isBlocked(cell, primary, hasKey, keyDoor)) {
                moveDir = primary
                // moving along one axis damps the other, so a slightly diagonal finger stays in the corridor
                if (horizontalFirst) dragAccumulatedR *= 0.5f else dragAccumulatedC *= 0.5f
            } else {
                // blocked along the main axis: forget it, try the secondary axis if the finger moved enough
                if (horizontalFirst) dragAccumulatedC = 0f else dragAccumulatedR = 0f
                if (secondary != null && !SlideSimulator.isBlocked(cell, secondary, hasKey, keyDoor)) {
                    moveDir = secondary
                } else {
                    if (secondary != null) {
                        if (horizontalFirst) dragAccumulatedR = 0f else dragAccumulatedC = 0f
                    }
                    bump()
                    break
                }
            }

            val nextCell = maze.getCell(currR + moveDir.dRow, currC + moveDir.dCol) ?: break
            currR = nextCell.row
            currC = nextCell.col
            movesAdded++
            consumeDragAxis(moveDir)

            // Key pickup
            if (keyDoor != null && !hasKey && currR == keyDoor.keyRow && currC == keyDoor.keyCol) {
                hasKey = true
                soundManager.playHint()
                hapticManager.victory()
            }

            // Portal teleport
            val dest = maze.mechanics.portal?.destinationFor(currR, currC)
            if (dest != null) {
                currR = dest.first
                currC = dest.second
                soundManager.playHint()
                hapticManager.hint()
                dragAccumulatedR = 0f
                dragAccumulatedC = 0f
                break
            }

            // Reached goal
            if (currR == maze.end.row && currC == maze.end.col) break
        }

        dragAccumulatedR = dragAccumulatedR.coerceIn(-0.5f, 0.5f)
        dragAccumulatedC = dragAccumulatedC.coerceIn(-0.5f, 0.5f)

        if (movesAdded > 0) {
            soundManager.playMove()
            val explored = if (maze.mechanics.hasFogOfWar) {
                computeExploredCells(maze, currR, currC, state.exploredCells)
            } else {
                state.exploredCells
            }

            _uiState.update {
                it.copy(
                    currentBallRow = currR.toFloat(),
                    currentBallCol = currC.toFloat(),
                    movesCount = it.movesCount + movesAdded,
                    hasKey = hasKey,
                    exploredCells = explored
                )
            }

            if (currR == maze.end.row && currC == maze.end.col) {
                onLevelCompleted()
            }
        }
    }

    private fun consumeDragAxis(dir: Direction) {
        when (dir) {
            Direction.RIGHT -> dragAccumulatedC = (dragAccumulatedC - 1f).coerceAtLeast(0f)
            Direction.LEFT -> dragAccumulatedC = (dragAccumulatedC + 1f).coerceAtMost(0f)
            Direction.DOWN -> dragAccumulatedR = (dragAccumulatedR - 1f).coerceAtLeast(0f)
            Direction.UP -> dragAccumulatedR = (dragAccumulatedR + 1f).coerceAtMost(0f)
        }
    }

    private fun handleDragEnd() {
        isDraggingActive = false
        dragAccumulatedR = 0f
        dragAccumulatedC = 0f
        _uiState.update {
            it.copy(
                currentBallRow = it.currentBallRow.roundToInt().toFloat(),
                currentBallCol = it.currentBallCol.roundToInt().toFloat()
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fog of war
    // ---------------------------------------------------------------------------------------

    /**
     * Adds every cell within the vision radius to the explored set. Returns the SAME set instance
     * when nothing new was discovered, so the 60fps slide animation doesn't copy a
     * 2000+ element set on every frame.
     */
    private fun computeExploredCells(
        maze: Maze,
        centerRow: Int,
        centerCol: Int,
        current: Set<Int>
    ): Set<Int> {
        var result: MutableSet<Int>? = null
        val radius = 3

        for (dr in -radius..radius) {
            for (dc in -radius..radius) {
                if (dr * dr + dc * dc > radius * radius + 1) continue
                val nr = centerRow + dr
                val nc = centerCol + dc
                if (nr !in 0 until maze.height || nc !in 0 until maze.width) continue
                val key = nr * maze.width + nc
                if (key in current) continue
                if (result == null) result = current.toMutableSet()
                result.add(key)
            }
        }
        return result ?: current
    }

    // ---------------------------------------------------------------------------------------
    // Hints
    // ---------------------------------------------------------------------------------------

    private fun requestHint() {
        val state = _uiState.value
        val maze = state.maze ?: return
        if (state.remainingHints <= 0 || !canPlay(state)) return

        val currR = state.currentBallRow.roundToInt().coerceIn(0, maze.height - 1)
        val currC = state.currentBallCol.roundToInt().coerceIn(0, maze.width - 1)

        val solveResult = MazeSolver.solve(
            grid = maze.grid,
            startRow = currR,
            startCol = currC,
            endRow = maze.end.row,
            endCol = maze.end.col,
            keyDoor = if (state.hasKey) null else maze.mechanics.keyDoor,
            portal = maze.mechanics.portal
        )

        if (!solveResult.isSolvable) return

        // Show only the next ~10 cells of the path
        val truncatedHint = solveResult.path.take(11)

        _uiState.update {
            it.copy(
                remainingHints = it.remainingHints - 1,
                showHint = true,
                hintPath = truncatedHint
            )
        }

        soundManager.playHint()
        hapticManager.hint()

        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(4000L)
            _uiState.update { it.copy(showHint = false) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Completion
    // ---------------------------------------------------------------------------------------

    /**
     * "Optimal" depends on how the player moves: a swipe move = one slide, a drag move = one cell.
     * Before, DRAG mode was compared with the swipe count, so it could essentially only ever
     * earn one star.
     */
    private fun optimalMovesFor(maze: Maze, mode: ControlMode): Int = when (mode) {
        ControlMode.SWIPE -> maze.optimalMoves
        ControlMode.DRAG -> (maze.optimalPath.size - 1).coerceAtLeast(1)
    }

    private fun onLevelCompleted() {
        if (completionStarted) return
        val state = _uiState.value
        val maze = state.maze ?: return
        completionStarted = true

        timerJob?.cancel()
        hazardJob?.cancel()
        hintJob?.cancel()
        bufferedDirection = null

        soundManager.playWin()
        hapticManager.victory()

        val optimal = optimalMovesFor(maze, state.controlMode)

        viewModelScope.launch(Dispatchers.Default) {
            for (step in 10 downTo 0) {
                _uiState.update { it.copy(ballScale = step / 10f) }
                delay(25L)
            }

            // Re-read after the animation: the timer was stopped, but be explicit about the values used
            val finalState = _uiState.value
            val timeMillis = finalState.elapsedSeconds * 1000L

            val score = ScoreCalculator.calculateStars(
                levelNumber = finalState.levelNumber,
                movesTaken = finalState.movesCount,
                optimalMoves = optimal,
                timeMillis = timeMillis,
                gridArea = maze.width * maze.height
            )

            try {
                if (isDailyChallenge) {
                    repository.saveDailyChallengeResult(
                        dateInt = dailyDateInt,
                        stars = score.stars,
                        timeMillis = timeMillis,
                        moves = finalState.movesCount
                    )
                } else {
                    repository.saveLevelResult(
                        levelNumber = finalState.levelNumber,
                        stars = score.stars,
                        timeMillis = timeMillis,
                        moves = finalState.movesCount
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A storage hiccup must never trap the player without the result dialog
            }

            _uiState.update {
                it.copy(
                    isCompleted = true,
                    completionScore = score,
                    completionOptimalMoves = optimal
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------------------------

    private fun getTutorialForLevel(level: Int): String? {
        return when (level) {
            6 -> "Fog of War: Corridors are hidden until explored. Your vision illuminates nearby paths."
            11 -> "Keys & Doors: Find the golden key before you can pass through the locked gate."
            16 -> "Portals: Step into a glowing portal vortex to teleport across the labyrinth."
            21 -> "Moving Hazards: Beware of patrolling hazards! Touching one resets you to the start."
            31 -> "Labyrinth Mastery: Multiple mechanics are now active together. Stay alert!"
            else -> null
        }
    }

    private fun dismissTutorial() {
        _uiState.update { it.copy(tutorialDismissed = true) }
    }

    private fun pauseGame() {
        val state = _uiState.value
        if (!state.isCompleted && !state.isLoading && !completionStarted) {
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    private fun resumeGame() {
        _uiState.update { it.copy(isPaused = false) }
    }

    private fun setLifecycleActive(active: Boolean) {
        isLifecycleActive = active
        // Leaving the app mid-run should not drop you back into a running maze
        if (!active) pauseGame()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        hazardJob?.cancel()
        moveAnimationJob?.cancel()
        hintJob?.cancel()
        loadJob?.cancel()
        pregenJob?.cancel()
    }

    companion object {
        private const val HAZARD_TICK_MS = 50L
        private const val HAZARD_HIT_RADIUS = 0.65f
        private const val BUMP_COOLDOWN_MS = 120L
        private const val SLIDE_SUBSTEPS = 4
        private const val DRAG_STEP_THRESHOLD = 0.38f

        fun provideFactory(
            levelNumber: Int,
            isDailyChallenge: Boolean = false,
            dailyDateInt: Int = 0,
            repository: GameRepository,
            soundManager: SoundEffectManager,
            hapticManager: HapticFeedbackManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GameViewModel(
                    initialLevel = levelNumber,
                    isDailyChallenge = isDailyChallenge,
                    dailyDateInt = dailyDateInt,
                    repository = repository,
                    soundManager = soundManager,
                    hapticManager = hapticManager
                ) as T
            }
        }
    }
}
