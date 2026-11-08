package com.example.ui.game

import com.example.domain.model.Direction

sealed interface GameUiAction {
    data class Swipe(val direction: Direction) : GameUiAction
    data class DragStart(val touchRow: Float, val touchCol: Float) : GameUiAction
    data class DragUpdate(val touchRow: Float, val touchCol: Float) : GameUiAction
    data object DragEnd : GameUiAction
    data object RequestHint : GameUiAction
    data object Pause : GameUiAction
    data object Resume : GameUiAction
    data object Restart : GameUiAction
    data object NextLevel : GameUiAction
    data class SetLifecycleActive(val isActive: Boolean) : GameUiAction
    data object DismissTutorial : GameUiAction

    /**
     * Size (dp) of the area available for the maze. The ViewModel owns the grid sizing, so a new
     * level always gets dimensions that fit the screen (previously "next level" fell back to a
     * hard-coded 12x20 grid, which left big empty bands around the maze).
     */
    data class UpdateAvailableSize(val widthDp: Float, val heightDp: Float) : GameUiAction
}
