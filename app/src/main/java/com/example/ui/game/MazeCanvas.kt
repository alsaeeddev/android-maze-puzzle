package com.example.ui.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.domain.model.Cell
import com.example.domain.model.ControlMode
import com.example.domain.model.Direction
import com.example.domain.model.Maze
import com.example.ui.theme.MazeGoalHoleDark
import com.example.ui.theme.MazeGoalRing
import com.example.ui.theme.MazeHintPath
import com.example.ui.theme.MazeWallColorDark
import com.example.ui.theme.MazeWallColorLight
import com.example.ui.theme.RetroAmber
import com.example.ui.theme.RetroCoral
import com.example.ui.theme.RetroCyan
import com.example.ui.theme.RetroDarkBackground
import com.example.ui.theme.RetroEmerald
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Colours that must differ between themes (neon colours are unreadable on the light floor). */
private class MazePalette(
    val wall: Color,
    val fog: Color,
    val start: Color,
    val goalRing: Color,
    val goalHole: Color,
    val key: Color,
    val door: Color,
    val portalRing: Color,
    val portalCore: Color,
    val hazard: Color,
    val hint: Color
) {
    companion object {
        val Dark = MazePalette(
            wall = MazeWallColorDark,
            fog = RetroDarkBackground,
            start = RetroEmerald,
            goalRing = MazeGoalRing,
            goalHole = MazeGoalHoleDark,
            key = RetroAmber,
            door = RetroCoral,
            portalRing = Color(0xFFB388FF),
            portalCore = Color(0xFF7C4DFF),
            hazard = RetroCoral,
            hint = MazeHintPath
        )
        val Light = MazePalette(
            wall = MazeWallColorLight,
            fog = Color(0xFF1E232B),
            start = Color(0xFF008947),
            goalRing = Color(0xFF007A87),
            goalHole = MazeGoalHoleDark,
            key = Color(0xFFB87800),
            door = Color(0xFFD32F2F),
            portalRing = Color(0xFF7C4DFF),
            portalCore = Color(0xFF5E35B1),
            hazard = Color(0xFFD32F2F),
            hint = Color(0xCC007A87)
        )
    }
}

private class MazeGeometry(val cellSize: Float, val offsetX: Float, val offsetY: Float)

private fun geometryFor(canvasWidth: Float, canvasHeight: Float, maze: Maze): MazeGeometry {
    val cellSize = min(canvasWidth / maze.width, canvasHeight / maze.height)
    return MazeGeometry(
        cellSize = cellSize,
        offsetX = (canvasWidth - maze.width * cellSize) / 2f,
        offsetY = (canvasHeight - maze.height * cellSize) / 2f
    )
}

/** Wall thickness in dp-based bounds (the old px bounds made walls look different per density). */
private fun Density.wallStroke(cellSize: Float): Float =
    (cellSize * 0.12f).coerceIn(1.5.dp.toPx(), 3.5.dp.toPx())

@Composable
fun MazeCanvas(
    maze: Maze,
    ballRow: Float,
    ballCol: Float,
    ballScale: Float,
    showHint: Boolean,
    hintPath: List<Cell>,
    controlMode: ControlMode,
    isDarkTheme: Boolean,
    hasKey: Boolean,
    exploredCells: Set<Int>,
    hazardTimeProvider: () -> Float,
    onSwipe: (Direction) -> Unit,
    onDragStart: (Float, Float) -> Unit = { _, _ -> },
    onDragUpdate: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = if (isDarkTheme) MazePalette.Dark else MazePalette.Light

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Boolean mask instead of Set<Pair>.contains() per cell per frame (that allocated thousands
    // of Pair objects every frame on big fog levels).
    val exploredMask = remember(exploredCells, maze) {
        BooleanArray(maze.width * maze.height).also { mask ->
            for (index in exploredCells) if (index in mask.indices) mask[index] = true
        }
    }

    // STATIC layer: walls + start marker. The lambda is remembered on (maze, palette) only, so the
    // wall Path is built once per maze. Before, the cache lambda captured the ball position, so
    // the whole wall Path (up to 2800 cells) was rebuilt on every animation frame.
    val staticLayer: CacheDrawScope.() -> DrawResult = remember(maze, palette) {
        {
            val g = geometryFor(size.width, size.height, maze)
            val strokeWidth = wallStroke(g.cellSize)
            val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

            val wallsPath = Path()
            for (r in 0 until maze.height) {
                for (c in 0 until maze.width) {
                    val cell = maze.grid[r][c]
                    val left = g.offsetX + c * g.cellSize
                    val top = g.offsetY + r * g.cellSize
                    val right = left + g.cellSize
                    val bottom = top + g.cellSize

                    if (cell.hasWall(Direction.UP)) {
                        wallsPath.moveTo(left, top)
                        wallsPath.lineTo(right, top)
                    }
                    if (cell.hasWall(Direction.LEFT)) {
                        wallsPath.moveTo(left, top)
                        wallsPath.lineTo(left, bottom)
                    }
                    if (r == maze.height - 1 && cell.hasWall(Direction.DOWN)) {
                        wallsPath.moveTo(left, bottom)
                        wallsPath.lineTo(right, bottom)
                    }
                    if (c == maze.width - 1 && cell.hasWall(Direction.RIGHT)) {
                        wallsPath.moveTo(right, top)
                        wallsPath.lineTo(right, bottom)
                    }
                }
            }

            val startCenter = Offset(
                g.offsetX + (maze.start.col + 0.5f) * g.cellSize,
                g.offsetY + (maze.start.row + 0.5f) * g.cellSize
            )

            onDrawBehind {
                drawPath(wallsPath, color = palette.wall, style = strokeStyle)
                drawCircle(palette.start.copy(alpha = 0.35f), radius = g.cellSize * 0.28f, center = startCenter)
                drawCircle(
                    color = palette.start,
                    radius = g.cellSize * 0.20f,
                    center = startCenter,
                    style = Stroke(width = strokeWidth * 0.8f)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(controlMode, maze) {
                var accumulatedX = 0f
                var accumulatedY = 0f
                var lastFired: Direction? = null
                val swipeThreshold = 18.dp.toPx()

                detectDragGestures(
                    onDragStart = { offset ->
                        accumulatedX = 0f
                        accumulatedY = 0f
                        lastFired = null
                        if (controlMode == ControlMode.DRAG) {
                            val g = geometryFor(size.width.toFloat(), size.height.toFloat(), maze)
                            onDragStart((offset.y - g.offsetY) / g.cellSize, (offset.x - g.offsetX) / g.cellSize)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (controlMode == ControlMode.SWIPE) {
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y

                            if (abs(accumulatedX) >= swipeThreshold || abs(accumulatedY) >= swipeThreshold) {
                                val dir = if (abs(accumulatedX) > abs(accumulatedY)) {
                                    if (accumulatedX > 0) Direction.RIGHT else Direction.LEFT
                                } else {
                                    if (accumulatedY > 0) Direction.DOWN else Direction.UP
                                }
                                // One long drag in the same direction must not fire repeated swipes
                                // (each extra one just bumped into the wall with sound + vibration).
                                if (dir != lastFired) {
                                    onSwipe(dir)
                                    lastFired = dir
                                }
                                accumulatedX = 0f
                                accumulatedY = 0f
                            }
                        } else {
                            val g = geometryFor(size.width.toFloat(), size.height.toFloat(), maze)
                            onDragUpdate(
                                (change.position.y - g.offsetY) / g.cellSize,
                                (change.position.x - g.offsetX) / g.cellSize
                            )
                        }
                    },
                    onDragEnd = { if (controlMode == ControlMode.DRAG) onDragEnd() },
                    onDragCancel = { if (controlMode == ControlMode.DRAG) onDragEnd() }
                )
            }
    ) {
        Spacer(modifier = Modifier.fillMaxSize().drawWithCache(staticLayer))

        // DYNAMIC layer: everything that moves or changes (cheap draw calls only)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val g = geometryFor(size.width, size.height, maze)
                    val cellSize = g.cellSize
                    val offsetX = g.offsetX
                    val offsetY = g.offsetY
                    val strokeWidth = wallStroke(cellSize)

                    fun cx(col: Float) = offsetX + (col + 0.5f) * cellSize
                    fun cy(row: Float) = offsetY + (row + 0.5f) * cellSize

                    // Goal hole
                    val endCenter = Offset(cx(maze.end.col.toFloat()), cy(maze.end.row.toFloat()))
                    val holeRadius = cellSize * 0.38f
                    drawCircle(
                        color = palette.goalRing.copy(alpha = 0.4f),
                        radius = holeRadius * pulse,
                        center = endCenter,
                        style = Stroke(width = strokeWidth * 0.9f)
                    )
                    drawCircle(palette.goalHole, radius = holeRadius, center = endCenter)
                    drawCircle(
                        color = palette.goalRing,
                        radius = holeRadius,
                        center = endCenter,
                        style = Stroke(width = strokeWidth * 0.7f)
                    )

                    // Key & Door
                    val keyDoor = maze.mechanics.keyDoor
                    if (keyDoor != null && !hasKey) {
                        val dLeft = offsetX + keyDoor.doorCol * cellSize
                        val dTop = offsetY + keyDoor.doorRow * cellSize
                        val doorWidth = strokeWidth * 1.5f
                        when (keyDoor.doorDirection) {
                            Direction.UP -> drawLine(palette.door, Offset(dLeft, dTop), Offset(dLeft + cellSize, dTop), strokeWidth = doorWidth)
                            Direction.DOWN -> drawLine(palette.door, Offset(dLeft, dTop + cellSize), Offset(dLeft + cellSize, dTop + cellSize), strokeWidth = doorWidth)
                            Direction.LEFT -> drawLine(palette.door, Offset(dLeft, dTop), Offset(dLeft, dTop + cellSize), strokeWidth = doorWidth)
                            Direction.RIGHT -> drawLine(palette.door, Offset(dLeft + cellSize, dTop), Offset(dLeft + cellSize, dTop + cellSize), strokeWidth = doorWidth)
                        }

                        val keyCenter = Offset(cx(keyDoor.keyCol.toFloat()), cy(keyDoor.keyRow.toFloat()))
                        drawCircle(palette.key.copy(alpha = 0.35f), radius = cellSize * 0.35f * pulse, center = keyCenter)
                        drawCircle(palette.key, radius = cellSize * 0.22f, center = keyCenter)
                    }

                    // Portals
                    val portal = maze.mechanics.portal
                    if (portal != null) {
                        val pRadius = cellSize * 0.32f
                        for ((row, col) in listOf(portal.rowA to portal.colA, portal.rowB to portal.colB)) {
                            val center = Offset(cx(col.toFloat()), cy(row.toFloat()))
                            drawCircle(palette.portalRing, radius = pRadius * pulse, center = center, style = Stroke(width = strokeWidth))
                            drawCircle(palette.portalCore, radius = pRadius * 0.65f, center = center)
                        }
                    }

                    // Hazards (time comes from the same clock the collision check uses)
                    if (maze.mechanics.hazards.isNotEmpty()) {
                        val time = hazardTimeProvider()
                        for (hazard in maze.mechanics.hazards) {
                            val (hr, hc) = hazard.positionAt(time)
                            val center = Offset(cx(hc), cy(hr))
                            val hRadius = cellSize * 0.26f
                            drawCircle(palette.hazard.copy(alpha = 0.4f), radius = hRadius * 1.3f * pulse, center = center)
                            drawCircle(palette.hazard, radius = hRadius, center = center)
                        }
                    }

                    // Hint path. Portal jumps are NOT adjacent cells: start a new segment there
                    // instead of drawing a line straight through the walls.
                    if (showHint && hintPath.size > 1) {
                        val path = Path()
                        var previous: Cell? = null
                        for (cell in hintPath) {
                            val x = cx(cell.col.toFloat())
                            val y = cy(cell.row.toFloat())
                            val prev = previous
                            val adjacent = prev != null && abs(prev.row - cell.row) + abs(prev.col - cell.col) == 1
                            if (adjacent) path.lineTo(x, y) else path.moveTo(x, y)
                            previous = cell
                        }
                        drawPath(
                            path,
                            color = palette.hint,
                            style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Fog of war
                    if (maze.mechanics.hasFogOfWar) {
                        val visionRadius = 2.85f
                        val cell = Size(cellSize, cellSize)
                        for (r in 0 until maze.height) {
                            for (c in 0 until maze.width) {
                                if (hypot(ballRow - r, ballCol - c) <= visionRadius) continue
                                val explored = exploredMask[r * maze.width + c]
                                drawRect(
                                    color = if (explored) palette.fog.copy(alpha = 0.62f) else palette.fog,
                                    topLeft = Offset(offsetX + c * cellSize, offsetY + r * cellSize),
                                    size = cell
                                )
                            }
                        }
                    }

                    // Ball
                    if (ballScale > 0.01f) {
                        val ballCenter = Offset(cx(ballCol), cy(ballRow))
                        val baseRadius = cellSize * 0.32f * ballScale

                        if (isDarkTheme) {
                            drawCircle(RetroCyan.copy(alpha = 0.55f), radius = baseRadius * 1.45f, center = ballCenter)
                            drawCircle(Color.White, radius = baseRadius, center = ballCenter)
                            drawCircle(
                                color = Color(0x33000000),
                                radius = baseRadius,
                                center = ballCenter,
                                style = Stroke(width = strokeWidth * 0.45f)
                            )
                        } else {
                            // drop shadow, cyan halo ring, dark marble, specular glint
                            drawCircle(
                                color = Color(0x35000000),
                                radius = baseRadius * 1.25f,
                                center = Offset(ballCenter.x + strokeWidth * 0.45f, ballCenter.y + strokeWidth * 0.45f)
                            )
                            drawCircle(
                                color = Color(0xFF0091EA),
                                radius = baseRadius * 1.15f,
                                center = ballCenter,
                                style = Stroke(width = strokeWidth * 0.75f)
                            )
                            drawCircle(Color(0xFF14171F), radius = baseRadius, center = ballCenter)
                            drawCircle(
                                color = Color(0xB3FFFFFF),
                                radius = baseRadius * 0.32f,
                                center = Offset(ballCenter.x - baseRadius * 0.32f, ballCenter.y - baseRadius * 0.32f)
                            )
                        }
                    }
                }
        )
    }
}
