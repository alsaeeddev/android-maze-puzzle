package com.example.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.LevelProgressEntity
import com.example.data.repository.GameRepository
import com.example.domain.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LevelItem(
    val levelNumber: Int,
    val isUnlocked: Boolean,
    val stars: Int,
    val bestTimeMillis: Long,
    val minMoves: Int,
    val difficulty: Difficulty
)

data class LevelSelectUiState(
    val selectedDifficulty: Difficulty = Difficulty.EASY,
    val levels: List<LevelItem> = emptyList(),
    val totalStars: Int = 0,
    val highestUnlockedLevel: Int = 1
)

class LevelSelectViewModel(private val repository: GameRepository) : ViewModel() {

    // null = "not chosen yet": open on the tab that contains the level the player is on,
    // instead of always showing Easy (level 120 players had to switch tabs every time).
    private val selectedDifficultyFlow = MutableStateFlow<Difficulty?>(null)

    val uiState: StateFlow<LevelSelectUiState> = combine(
        selectedDifficultyFlow,
        repository.getAllProgress(),
        repository.getHighestUnlockedLevel(),
        repository.getTotalStars()
    ) { selected, allProgress, highestUnlocked, totalStars ->
        val progressMap = allProgress.associateBy { it.levelNumber }
        val maxUnlocked = highestUnlocked.coerceAtLeast(1)
        val diff = selected ?: Difficulty.forLevel(maxUnlocked)

        val items = diff.levelRange.map { lvl ->
            val progress: LevelProgressEntity? = progressMap[lvl]
            LevelItem(
                levelNumber = lvl,
                isUnlocked = lvl <= maxUnlocked,
                stars = progress?.stars ?: 0,
                bestTimeMillis = progress?.bestTimeMillis ?: 0L,
                minMoves = progress?.minMoves ?: 0,
                difficulty = diff
            )
        }

        LevelSelectUiState(
            selectedDifficulty = diff,
            levels = items,
            totalStars = totalStars,
            highestUnlockedLevel = maxUnlocked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LevelSelectUiState()
    )

    fun selectDifficulty(difficulty: Difficulty) {
        selectedDifficultyFlow.value = difficulty
    }

    companion object {
        fun provideFactory(repository: GameRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LevelSelectViewModel(repository) as T
                }
            }
    }
}
