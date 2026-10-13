package com.aegisvault.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun TestScope.store(file: File, notice: MutableStateFlow<SettingsNotice?> = MutableStateFlow(null)) =
        PreferenceDataStoreFactory.create(corruptionHandler = settingsCorruptionHandler(notice),
            scope = backgroundScope, produceFile = { file })
    private fun file(name: String = "settings") = File(temporary.root, "$name.preferences_pb")

    @Test fun unknownAndOldEnumsPreserveLegalSecurityThenEditSucceeds() = runTest {
        val store = store(file())
        store.edit {
            it[stringPreferencesKey("language")] = "old-en"
            it[stringPreferencesKey("theme")] = "FUTURE_THEME"
            it[stringPreferencesKey("clipboard_auto_clear")] = "S60"
            it[booleanPreferencesKey("protect_screen")] = true
            it[booleanPreferencesKey("auto_clear_on_leave")] = true
        }
        val repo = AppPreferencesRepository(store)
        val initial = repo.settings.first()
        assertEquals(LanguageOption.SYSTEM, initial.language)
        assertEquals(ThemeOption.SYSTEM, initial.theme)
        assertTrue(initial.protectScreen); assertTrue(initial.autoClearOnLeave)
        assertEquals(ClipboardAutoClear.S60, initial.clipboardAutoClear)
        assertEquals(SettingsNotice.VALUES_RECOVERED, repo.notices.value)
        repo.update { it.copy(theme = ThemeOption.DARK) }
        assertEquals(initial.copy(theme = ThemeOption.DARK), repo.settings.first())
        assertEquals("SYSTEM", store.data.first()[stringPreferencesKey("language")])
        repo.acknowledgeNotice(); assertNull(repo.notices.value)
        assertEquals(setOf("language", "theme", "protect_screen", "auto_clear_on_leave", "clipboard_auto_clear"),
            store.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test fun missingValuesAreNormalInstallationDefaults() = runTest {
        val repo = AppPreferencesRepository(store(file()))
        assertEquals(AppSettings(), repo.settings.first()); assertNull(repo.notices.value)
        repo.update { it.copy(language = LanguageOption.ZH_CN) }
        assertEquals(LanguageOption.ZH_CN, repo.settings.first().language)
    }

    @Test fun everyLegalEnumAndFalseSafetyChoiceSurvivesRealPersistence() = runTest {
        val store = store(file()); val repo = AppPreferencesRepository(store)
        for (language in LanguageOption.entries) for (theme in ThemeOption.entries)
            for (clipboard in ClipboardAutoClear.entries) {
                val expected = AppSettings(language, theme, false, false, clipboard)
                repo.update { expected }; assertEquals(expected, repo.settings.first())
                assertFalse(SettingsCodec.decode(store.data.first()).recovered)
            }
        assertNull(repo.notices.value)
    }

    @Test fun wrongTypesRecoverWithoutClassCastAndUseProtectiveSafetyDefaults() = runTest {
        val store = store(file())
        store.edit { preferences ->
            for (name in listOf("language", "theme", "clipboard_auto_clear", "protect_screen", "auto_clear_on_leave"))
                preferences[intPreferencesKey(name)] = 123
        }
        val repo = AppPreferencesRepository(store)
        assertEquals(SettingsCodec.protectiveDefaults, repo.settings.first())
        assertEquals(SettingsNotice.VALUES_RECOVERED, repo.notices.value)
        repo.update { it.copy(theme = ThemeOption.LIGHT) }
        assertEquals(SettingsCodec.protectiveDefaults.copy(theme = ThemeOption.LIGHT), repo.settings.first())
        assertFalse(SettingsCodec.decode(store.data.first()).recovered)
    }

    @Test fun corruptProtobufUsesActualHandlerAndNextEditSucceeds() = runTest {
        val file = file(); file.writeBytes(byteArrayOf(0x0a, 0x7f, 0x01))
        val notice = MutableStateFlow<SettingsNotice?>(null)
        val repo = AppPreferencesRepository(store(file, notice), notice)
        assertEquals(SettingsCodec.protectiveDefaults, repo.settings.first())
        assertEquals(SettingsNotice.STORE_RESET, repo.notices.value)
        repo.update { it.copy(language = LanguageOption.ENGLISH) }
        assertEquals(LanguageOption.ENGLISH, repo.settings.first().language)
        assertTrue(repo.settings.first().protectScreen)
        assertEquals(SettingsNotice.STORE_RESET, repo.notices.value)
        assertTrue(file.length() > 3)
    }

    @Test fun actualFileReadIoRetriesAndSubscriptionReceivesLaterEdit() = runTest {
        val file = file("blocked"); assertTrue(file.mkdir())
        val repo = AppPreferencesRepository(store(file))
        val received = mutableListOf<AppSettings>()
        backgroundScope.launch { repo.settings.collect { received.add(it) } }
        runCurrent()
        assertEquals(SettingsNotice.READ_RETRYING, repo.notices.value)
        assertEquals(SettingsCodec.protectiveDefaults, received.last())
        assertTrue(file.delete())
        advanceTimeBy(1000); runCurrent()
        assertEquals(AppSettings(), received.last())
        assertEquals(SettingsNotice.READ_RECOVERED, repo.notices.value)
        repo.update { it.copy(protectScreen = true, language = LanguageOption.ZH_CN) }; runCurrent()
        assertTrue(received.last().protectScreen)
        assertEquals(LanguageOption.ZH_CN, received.last().language)
    }

    @Test fun actualFileWriteIoLeavesOldValuesAndNextEditSucceeds() = runTest {
        val file = file(); val repo = AppPreferencesRepository(store(file))
        repo.update { it.copy(protectScreen = true, clipboardAutoClear = ClipboardAutoClear.S60) }
        val previous = repo.settings.first()
        val backup = File(temporary.root, "settings-backup"); assertTrue(file.renameTo(backup))
        assertTrue(file.mkdir())
        try { repo.update { it.copy(protectScreen = false) }; fail("Expected real file I/O error") }
        catch (_: IOException) { }
        assertEquals(previous, repo.settings.first())
        assertEquals(SettingsNotice.WRITE_FAILED, repo.notices.value)
        assertTrue(file.delete()); assertTrue(backup.renameTo(file))
        repo.update { it.copy(theme = ThemeOption.DARK) }
        assertEquals(previous.copy(theme = ThemeOption.DARK), repo.settings.first())
    }

    @Test fun cancellationAndProgrammingErrorsAreNotRecovered() = runTest {
        val repo = AppPreferencesRepository(store(file()))
        try { repo.update { throw IllegalArgumentException("programming failure") }; fail("Expected programming failure") }
        catch (_: IllegalArgumentException) { }
        try { repo.update { throw CancellationException("cancel") }; fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertNull(repo.notices.value)
        assertEquals(AppSettings(), repo.settings.first())
    }

    @Test fun retryAfterKnownSettingsKeepsProtectionRatherThanEmittingEmptyDefaults() = runTest {
        val store = store(file())
        val known = AppSettings(protectScreen = true, autoClearOnLeave = true, clipboardAutoClear = ClipboardAutoClear.S300)
        store.edit { SettingsCodec.write(it, known) }
        var first = true
        val interrupted = object : androidx.datastore.core.DataStore<Preferences> by store {
            override val data: Flow<Preferences> = flow {
                if (first) { first = false; emit(store.data.first()); throw IOException("injected read interruption") }
                emitAll(store.data)
            }
        }
        val repo = AppPreferencesRepository(interrupted)
        val received = mutableListOf<AppSettings>()
        backgroundScope.launch { repo.settings.collect { received.add(it) } }; runCurrent()
        assertEquals(listOf(known, known), received)
        assertEquals(SettingsNotice.READ_RETRYING, repo.notices.value)
        advanceTimeBy(1000); runCurrent()
        assertEquals(known, received.last())
        assertEquals(SettingsNotice.READ_RECOVERED, repo.notices.value)
        repo.update { it.copy(theme = ThemeOption.LIGHT) }; runCurrent()
        assertEquals(known.copy(theme = ThemeOption.LIGHT), received.last())
    }

    @Test fun lostRecordAfterLoadKeepsKnownSafetyAndCanBeRepairedByEdit() = runTest {
        val store = store(file()); val repo = AppPreferencesRepository(store)
        val known = AppSettings(protectScreen = true, autoClearOnLeave = true, clipboardAutoClear = ClipboardAutoClear.S60)
        repo.update { known }; assertEquals(known, repo.settings.first())
        store.edit { it.clear() }
        assertEquals(known, repo.settings.first())
        assertEquals(SettingsNotice.VALUES_RECOVERED, repo.notices.value)
        repo.update { it.copy(theme = ThemeOption.DARK) }
        assertEquals(known.copy(theme = ThemeOption.DARK), repo.settings.first())
        assertTrue(store.data.first()[booleanPreferencesKey("protect_screen")]!!)
    }

    @Test fun readCancellationAndProgrammingErrorsPropagateWithoutDefaultEmission() = runTest {
        val store = store(file())
        for (error in listOf(IllegalStateException("programming failure"), CancellationException("cancel"))) {
            val broken = object : androidx.datastore.core.DataStore<Preferences> by store {
                override val data: Flow<Preferences> = flow { throw error }
            }
            val repo = AppPreferencesRepository(broken)
            try { repo.settings.first(); fail("Expected original failure") }
            catch (received: Exception) { assertSame(error, received) }
            assertNull(repo.notices.value)
        }
    }

    @Test fun freshDataStoreReadsUnknownEnumsAndWrongTypesFromActualProtobufFile() = runTest {
        val file = file()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val writer = PreferenceDataStoreFactory.create(scope = owner, produceFile = { file })
        writer.edit {
            it[stringPreferencesKey("language")] = "future-language"
            it[intPreferencesKey("theme")] = 99
            it[booleanPreferencesKey("protect_screen")] = true
            it[booleanPreferencesKey("auto_clear_on_leave")] = true
            it[stringPreferencesKey("clipboard_auto_clear")] = "S300"
        }
        owner.cancel(); owner.coroutineContext[Job]!!.join()
        val reader = AppPreferencesRepository(store(file))
        val expected = AppSettings(protectScreen = true, autoClearOnLeave = true, clipboardAutoClear = ClipboardAutoClear.S300)
        assertEquals(expected, reader.settings.first())
        assertEquals(SettingsNotice.VALUES_RECOVERED, reader.notices.value)
        reader.update { it.copy(language = LanguageOption.ENGLISH) }
        assertEquals(expected.copy(language = LanguageOption.ENGLISH), reader.settings.first())
    }
}
