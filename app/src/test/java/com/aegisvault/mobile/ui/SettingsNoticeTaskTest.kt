package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModelStore
import com.aegisvault.mobile.data.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsNoticeTaskTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private class Repo : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings(protectScreen = true, autoClearOnLeave = true))
        override val notices = MutableStateFlow<SettingsNotice?>(null)
        var failWrite = false
        override fun acknowledgeNotice() { notices.value = null }
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            if (failWrite) throw IOException("injected write failure")
            settings.value = transform(settings.value)
        }
    }

    @Test fun recoveryNoticeSurvivesActionsAndSensitiveClearUntilAcknowledged() = runTest {
        val repo = Repo(); val vm = AegisVaultViewModel(repo, StandardTestDispatcher(testScheduler))
        repo.notices.value = SettingsNotice.STORE_RESET; runCurrent()
        assertTrue(vm.uiState.value.settingsLoaded); assertTrue(vm.uiState.value.settings.protectScreen)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("public text")); vm.onEvent(AegisVaultUiEvent.Encode)
        advanceUntilIdle(); assertEquals(SettingsNotice.STORE_RESET, vm.uiState.value.settingsNotice)
        vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
        assertEquals(SettingsNotice.STORE_RESET, vm.uiState.value.settingsNotice)
        vm.onEvent(AegisVaultUiEvent.AcknowledgeSettingsNotice); runCurrent()
        assertNull(vm.uiState.value.settingsNotice); assertNull(repo.notices.value)
        ViewModelStore().apply { put("vm", vm); clear() }
    }

    @Test fun writeIoIsVisibleAndLaterSettingsEditStillWorks() = runTest {
        val repo = Repo(); val vm = AegisVaultViewModel(repo, StandardTestDispatcher(testScheduler)); runCurrent()
        repo.failWrite = true; vm.updateSettings { it.copy(protectScreen = false) }; runCurrent()
        assertTrue(vm.uiState.value.settings.protectScreen)
        assertEquals(SettingsNotice.WRITE_FAILED, vm.uiState.value.settingsNotice)
        repo.failWrite = false; vm.updateSettings { it.copy(theme = ThemeOption.DARK) }; runCurrent()
        assertEquals(ThemeOption.DARK, vm.uiState.value.settings.theme)
        assertTrue(vm.uiState.value.settings.protectScreen)
        ViewModelStore().apply { put("vm", vm); clear() }
    }

    @Test fun leavingBeforeFirstSettingsLoadClearsButLoadedFalseChoiceIsHonored() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : AppSettingsRepository {
            override val settings = kotlinx.coroutines.flow.flow {
                gate.await(); emit(AppSettings(autoClearOnLeave = false))
            }
            override suspend fun update(transform: (AppSettings) -> AppSettings) { }
        }
        val vm = AegisVaultViewModel(repo, StandardTestDispatcher(testScheduler))
        assertFalse(vm.uiState.value.settingsLoaded)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("pending")); vm.onEvent(AegisVaultUiEvent.UpdatePassword("secret"))
        vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        assertEquals("", vm.uiState.value.inputText); assertEquals("", vm.uiState.value.password)
        gate.complete(Unit); runCurrent(); assertTrue(vm.uiState.value.settingsLoaded)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("keep")); vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        assertEquals("keep", vm.uiState.value.inputText)
        ViewModelStore().apply { put("vm", vm); clear() }
    }
}
