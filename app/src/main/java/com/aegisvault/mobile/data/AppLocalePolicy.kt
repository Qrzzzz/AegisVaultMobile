package com.aegisvault.mobile.data

internal data class LocaleSync(val apply: LanguageOption? = null, val store: LanguageOption? = null)

/** API 33+ framework locale is authoritative after the one-time legacy preference handoff. */
internal fun localeSync(stored: LanguageOption, system: LanguageOption, framework: Boolean, migrated: Boolean): LocaleSync =
    when {
        !framework -> LocaleSync(apply = stored)
        !migrated && system == LanguageOption.SYSTEM && stored != LanguageOption.SYSTEM -> LocaleSync(apply = stored)
        system != stored -> LocaleSync(store = system)
        else -> LocaleSync()
    }
