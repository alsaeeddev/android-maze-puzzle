package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ControlMode
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.gamePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "as_preferences")

data class GameSettings(
    val controlMode: ControlMode = ControlMode.SWIPE,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class GamePreferencesRepository(private val context: Context) {

    private object PreferenceKeys {
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<GameSettings> = context.gamePreferencesDataStore.data.map { prefs ->
        val controlModeStr = prefs[PreferenceKeys.CONTROL_MODE] ?: ControlMode.SWIPE.name
        val themeModeStr = prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name

        val controlMode = try {
            ControlMode.valueOf(controlModeStr)
        } catch (_: Exception) {
            ControlMode.SWIPE
        }

        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        GameSettings(
            controlMode = controlMode,
            soundEnabled = prefs[PreferenceKeys.SOUND_ENABLED] ?: true,
            hapticsEnabled = prefs[PreferenceKeys.HAPTICS_ENABLED] ?: true,
            themeMode = themeMode
        )
    }

    suspend fun setControlMode(mode: ControlMode) {
        context.gamePreferencesDataStore.edit { prefs ->
            prefs[PreferenceKeys.CONTROL_MODE] = mode.name
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.gamePreferencesDataStore.edit { prefs ->
            prefs[PreferenceKeys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.gamePreferencesDataStore.edit { prefs ->
            prefs[PreferenceKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.gamePreferencesDataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun resetAll() {
        context.gamePreferencesDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
