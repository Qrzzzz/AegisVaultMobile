package com.aegisvault.mobile.ui

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aegisvault.mobile.data.LanguageOption
import com.aegisvault.mobile.data.localeSync

internal class AppLocaleBridge(context: Context) {
    // Only a non-sensitive migration marker. Existing language values stay in DataStore.
    private val preferences = context.applicationContext.getSharedPreferences("app_locale_bridge", Context.MODE_PRIVATE)

    fun synchronize(stored: LanguageOption, update: (LanguageOption) -> Unit) {
        val locales = AppCompatDelegate.getApplicationLocales()
        val system = when (locales[0]?.language) {
            "en" -> LanguageOption.ENGLISH
            "zh" -> LanguageOption.ZH_CN
            else -> LanguageOption.SYSTEM
        }
        val framework = Build.VERSION.SDK_INT >= 33
        val plan = localeSync(stored, system, framework, preferences.getBoolean("framework_migrated", false))
        plan.apply?.let(::apply)
        if (framework) preferences.edit().putBoolean("framework_migrated", true).apply()
        plan.store?.let(update)
    }

    fun select(language: LanguageOption) {
        // Before API 33, apply only after the DataStore write succeeds.
        if (Build.VERSION.SDK_INT >= 33) {
            preferences.edit().putBoolean("framework_migrated", true).apply()
            apply(language)
        }
    }

    private fun apply(language: LanguageOption) {
        val tag = when (language) {
            LanguageOption.SYSTEM -> ""
            LanguageOption.ENGLISH -> "en"
            LanguageOption.ZH_CN -> "zh-CN"
        }
        val desired = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != desired) AppCompatDelegate.setApplicationLocales(desired)
    }
}
