package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class HomeUiState(
    val currentLevel: Int = 1,
    val totalStars: Int = 0,
    val totalCompletedLevels: Int = 0,
    val todayDateInt: Int = 0,
    val isDailyCompleted: Boolean = false,
    val dailyBestTimeMillis: Long = 0L
)

class HomeViewModel(private val repository: GameRepository) : ViewModel() {

    /**
     * Today's date as yyyyMMdd. It used to be computed once when the ViewModel was created, so an
     * app left open past midnight kept showing (and launching) yesterday's daily challenge.
     * [refreshDate] is called whenever the screen is resumed.
     */
    private val todayFlow = MutableStateFlow(currentDateInt())

    fun refreshDate() {
        todayFlow.value = currentDateInt()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = todayFlow.flatMapLatest { today ->
        combine(
            repository.getHighestUnlockedLevel(),
            repository.getTotalStars(),
            repository.getAllProgress(),
            repository.getDailyChallenge(today)
        ) { highestUnlocked, stars, allProgress, dailyChallenge ->
            HomeUiState(
                currentLevel = highestUnlocked.coerceAtLeast(1),
                totalStars = stars,
                totalCompletedLevels = allProgress.size,
                todayDateInt = today,
                isDailyCompleted = dailyChallenge?.completed ?: false,
                dailyBestTimeMillis = dailyChallenge?.bestTimeMillis ?: 0L
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(todayDateInt = todayFlow.value)
    )

    private fun currentDateInt(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }

    companion object {
        fun provideFactory(repository: GameRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository) as T
                }
            }
    }
}
