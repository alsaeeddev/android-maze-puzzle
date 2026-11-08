package com.example.di

import android.content.Context
import com.example.data.db.MazeDatabase
import com.example.data.haptics.HapticFeedbackManager
import com.example.data.preferences.GamePreferencesRepository
import com.example.data.repository.GameRepository
import com.example.data.repository.GameRepositoryImpl
import com.example.data.sound.SoundEffectManager

interface AppContainer {
    val gameRepository: GameRepository
    val soundEffectManager: SoundEffectManager
    val hapticFeedbackManager: HapticFeedbackManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: MazeDatabase by lazy {
        MazeDatabase.getInstance(context)
    }

    private val preferencesRepository: GamePreferencesRepository by lazy {
        GamePreferencesRepository(context)
    }

    override val gameRepository: GameRepository by lazy {
        GameRepositoryImpl(
            levelProgressDao = database.levelProgressDao(),
            dailyChallengeDao = database.dailyChallengeDao(),
            preferencesRepository = preferencesRepository
        )
    }

    override val soundEffectManager: SoundEffectManager by lazy {
        SoundEffectManager(context)
    }

    override val hapticFeedbackManager: HapticFeedbackManager by lazy {
        HapticFeedbackManager(context)
    }
}
