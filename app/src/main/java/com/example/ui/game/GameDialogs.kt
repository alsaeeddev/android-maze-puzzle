package com.example.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.scoring.ScoreCalculator
import java.util.Locale

/** Locale-proof "mm:ss" (default-locale formatting can render Arabic-Indic digits). */
internal fun formatTime(totalSeconds: Int): String =
    String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)

/**
 * Custom dialog shell. The stock AlertDialog always reserved an (empty) button row plus extra
 * bottom padding when `confirmButton = {}` was used, which looked like stray empty space, and
 * its content was not scrollable on short screens / landscape.
 */
@Composable
private fun GameDialog(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
fun PauseDialog(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    GameDialog(onDismissRequest = onResume, dismissOnClickOutside = true) {
        Text(
            text = stringResource(id = R.string.pause),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onResume,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("resume_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.resume))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("restart_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.restart))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("exit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.GridOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.exit_to_menu))
        }
    }
}

@Composable
fun WinDialog(
    levelNumber: Int,
    isDaily: Boolean,
    score: ScoreCalculator.LevelScore,
    elapsedSeconds: Int,
    movesTaken: Int,
    optimalMoves: Int,
    onNextLevel: () -> Unit,
    onReplay: () -> Unit,
    onLevelSelect: () -> Unit
) {
    val starScales = remember { List(3) { Animatable(0f) } }

    LaunchedEffect(score.stars) {
        for (i in 0 until score.stars.coerceIn(0, 3)) {
            starScales[i].animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    // Back button leaves to the menu; tapping outside is ignored so a stray tap can't dismiss it.
    GameDialog(onDismissRequest = onLevelSelect, dismissOnClickOutside = false) {
        Text(
            text = stringResource(id = R.string.level_complete),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isDaily) "Daily Challenge" else stringResource(id = R.string.level_title, levelNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 14.dp)
        ) {
            val starSizes = listOf(38.dp, 46.dp, 38.dp)
            for (i in 0 until 3) {
                val earned = score.stars >= i + 1
                if (i > 0) Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (earned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    // tertiary = readable amber on both themes (plain neon amber vanishes on white)
                    tint = if (earned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .size(starSizes[i])
                        .scale(if (earned) starScales[i].value else 1f)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatRow(label = stringResource(id = R.string.time), value = formatTime(elapsedSeconds))
                StatRow(
                    label = stringResource(id = R.string.moves),
                    value = "$movesTaken (Opt: $optimalMoves)"
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (!isDaily) {
            Button(
                onClick = onNextLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("win_next_level_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.next_level),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReplay,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("win_replay_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.restart))
            }

            OutlinedButton(
                onClick = onLevelSelect,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("win_levels_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.GridOn, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = if (isDaily) R.string.exit_to_menu else R.string.levels))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
