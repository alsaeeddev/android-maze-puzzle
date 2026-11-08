package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.GameSettings
import com.example.data.repository.GameRepository
import com.example.domain.model.ControlMode
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: GameSettings = GameSettings(),
    val showResetDialog: Boolean = false
)

class SettingsViewModel(private val repository: GameRepository) : ViewModel() {

    private val showResetDialogFlow = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.settingsFlow,
        showResetDialogFlow
    ) { settings, showReset ->
        SettingsUiState(
            settings = settings,
            showResetDialog = showReset
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setControlMode(mode: ControlMode) {
        viewModelScope.launch {
            repository.setControlMode(mode)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSoundEnabled(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticsEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun showResetConfirmation(show: Boolean) {
        showResetDialogFlow.value = show
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            showResetDialogFlow.value = false
        }
    }

    companion object {
        fun provideFactory(repository: GameRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
