package com.aegisvault.mobile.data

import org.junit.Assert.*
import org.junit.Test

class AppLocalePolicyTest {
    @Test fun olderAndroidAlwaysUsesTheExistingStoredSelection() {
        for (stored in LanguageOption.entries) for (system in LanguageOption.entries)
            assertEquals(LocaleSync(apply = stored), localeSync(stored, system, false, false))
    }

    @Test fun legacyExplicitSelectionIsHandedToAnEmptyFrameworkOnlyOnce() {
        for (stored in listOf(LanguageOption.ENGLISH, LanguageOption.ZH_CN))
            assertEquals(LocaleSync(apply = stored), localeSync(stored, LanguageOption.SYSTEM, true, false))
    }

    @Test fun existingFrameworkSelectionWinsOverLegacyPreferences() {
        assertEquals(LocaleSync(store = LanguageOption.ZH_CN),
            localeSync(LanguageOption.ENGLISH, LanguageOption.ZH_CN, true, false))
    }

    @Test fun changingSystemAppLanguageBackToFollowSystemDoesNotRestoreTheOldPreference() {
        assertEquals(LocaleSync(store = LanguageOption.SYSTEM),
            localeSync(LanguageOption.ZH_CN, LanguageOption.SYSTEM, true, true))
    }

    @Test fun matchedFrameworkAndStoreDoNotCauseRecreationOrAnotherWrite() {
        for (language in LanguageOption.entries)
            assertEquals(LocaleSync(), localeSync(language, language, true, true))
    }
}
