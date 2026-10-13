package com.aegisvault.mobile.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsCodec {
    val protectiveDefaults = AppSettings(protectScreen = true, autoClearOnLeave = true,
        clipboardAutoClear = ClipboardAutoClear.S30)
    data class Decoded(val settings: AppSettings, val recovered: Boolean)
    fun hasStoredValues(preferences: Preferences) = preferences.asMap().keys.any {
        it.name in setOf("language", "theme", "protect_screen", "auto_clear_on_leave", "clipboard_auto_clear")
    }

    fun decode(preferences: Preferences): Decoded {
        // Persisted Preferences are keyed by name and are not a type-safe schema.
        val values = preferences.asMap().mapKeys { it.key.name }
        var recovered = false
        fun <T> enum(name: String, entries: Array<T>, default: T, storedName: (T) -> String): T {
            val raw = values[name] ?: return default
            return entries.firstOrNull { raw is String && storedName(it) == raw }
                ?: default.also { recovered = true }
        }
        fun boolean(name: String): Boolean {
            val raw = values[name] ?: return false
            return (raw as? Boolean) ?: true.also { recovered = true }
        }
        return Decoded(AppSettings(
            language = enum("language", LanguageOption.entries.toTypedArray(), LanguageOption.SYSTEM) { it.name },
            theme = enum("theme", ThemeOption.entries.toTypedArray(), ThemeOption.SYSTEM) { it.name },
            protectScreen = boolean("protect_screen"),
            autoClearOnLeave = boolean("auto_clear_on_leave"),
            clipboardAutoClear = if (!values.containsKey("clipboard_auto_clear")) ClipboardAutoClear.OFF else
                enum("clipboard_auto_clear", ClipboardAutoClear.entries.toTypedArray(), ClipboardAutoClear.S30) { it.name },
        ), recovered)
    }

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[stringPreferencesKey("language")] = settings.language.name
        preferences[stringPreferencesKey("theme")] = settings.theme.name
        preferences[booleanPreferencesKey("protect_screen")] = settings.protectScreen
        preferences[booleanPreferencesKey("auto_clear_on_leave")] = settings.autoClearOnLeave
        preferences[stringPreferencesKey("clipboard_auto_clear")] = settings.clipboardAutoClear.name
    }
}
