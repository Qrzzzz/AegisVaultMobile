package com.aegisvault.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("aegisvault_settings")

class AppPreferencesRepository(private val context: Context) : AppSettingsRepository {
    override val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = LanguageOption.valueOf(p[KEY_LANGUAGE] ?: LanguageOption.SYSTEM.name),
            theme = ThemeOption.valueOf(p[KEY_THEME] ?: ThemeOption.SYSTEM.name),
            protectScreen = p[KEY_PROTECT_SCREEN] ?: false,
            autoClearOnLeave = p[KEY_AUTO_CLEAR_ON_LEAVE] ?: false,
            clipboardAutoClear = ClipboardAutoClear.valueOf(p[KEY_CLIPBOARD_AUTO_CLEAR] ?: ClipboardAutoClear.OFF.name),
        )
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val old = AppSettings(
                language = LanguageOption.valueOf(prefs[KEY_LANGUAGE] ?: LanguageOption.SYSTEM.name),
                theme = ThemeOption.valueOf(prefs[KEY_THEME] ?: ThemeOption.SYSTEM.name),
                protectScreen = prefs[KEY_PROTECT_SCREEN] ?: false,
                autoClearOnLeave = prefs[KEY_AUTO_CLEAR_ON_LEAVE] ?: false,
                clipboardAutoClear = ClipboardAutoClear.valueOf(prefs[KEY_CLIPBOARD_AUTO_CLEAR] ?: ClipboardAutoClear.OFF.name),
            )
            val n = transform(old)
            prefs[KEY_LANGUAGE] = n.language.name
            prefs[KEY_THEME] = n.theme.name
            prefs[KEY_PROTECT_SCREEN] = n.protectScreen
            prefs[KEY_AUTO_CLEAR_ON_LEAVE] = n.autoClearOnLeave
            prefs[KEY_CLIPBOARD_AUTO_CLEAR] = n.clipboardAutoClear.name
        }
    }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_PROTECT_SCREEN = booleanPreferencesKey("protect_screen")
        val KEY_AUTO_CLEAR_ON_LEAVE = booleanPreferencesKey("auto_clear_on_leave")
        val KEY_CLIPBOARD_AUTO_CLEAR = stringPreferencesKey("clipboard_auto_clear")
    }
}
