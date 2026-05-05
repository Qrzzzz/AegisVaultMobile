package com.aegisvault.mobile.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(
    val storageValue: String,
    val localeTag: String,
) {
    ZH_CN("zh-CN", "zh-CN"),
    ENGLISH("en", "en");

    companion object {
        fun fromStorage(value: String?): AppLanguage {
            val normalized = value?.trim()?.lowercase()
            return when (normalized) {
                "zh", "zh-cn", "zh_cn", "cn" -> ZH_CN
                "en", "en-us", "en_us" -> ENGLISH
                else -> ZH_CN
            }
        }
    }
}

data class AppPreferenceState(
    val language: AppLanguage,
)

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppPreferenceState {
        return AppPreferenceState(
            language = AppLanguage.fromStorage(preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE.storageValue)),
        )
    }

    fun updateLanguage(language: AppLanguage): AppPreferenceState {
        preferences.edit().putString(KEY_LANGUAGE, language.storageValue).apply()
        Locale.setDefault(Locale.forLanguageTag(language.localeTag))
        return load()
    }

    companion object {
        private const val PREFS_NAME = "aegisvault_mobile_prefs"
        private const val KEY_LANGUAGE = "language_tag"
        val DEFAULT_LANGUAGE = AppLanguage.ZH_CN

        fun readLanguage(context: Context): AppLanguage {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppLanguage.fromStorage(preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE.storageValue))
        }

        fun wrapContext(base: Context): Context {
            val language = readLanguage(base)
            val locale = Locale.forLanguageTag(language.localeTag)
            Locale.setDefault(locale)

            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocales(LocaleList(locale))
            }

            return base.createConfigurationContext(configuration)
        }
    }
}
