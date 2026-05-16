package com.aegisvault.mobile.ui

import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AegisVaultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepo(initial: AppSettings = AppSettings()) : AppSettingsRepository {
        private val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow.asStateFlow()
        override suspend fun update(transform: (AppSettings) -> AppSettings) { flow.value = transform(flow.value) }
    }

    @Test fun clearSensitiveDataClearsInputPasswordResult() = runTest {
        val vm = vm(this)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("abc"))
        vm.onEvent(AegisVaultUiEvent.UpdatePassword("pwd"))
        vm.onEvent(AegisVaultUiEvent.Encode)
        advanceUntilIdle()
        vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
        assertEquals("", vm.uiState.value.inputText)
        assertEquals("", vm.uiState.value.password)
        assertEquals("", vm.uiState.value.resultText)
    }

    @Test fun useResultAsInputMovesAndClearsResult() = runTest {
        val vm = vm(this)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("abc"))
        vm.onEvent(AegisVaultUiEvent.Encode)
        advanceUntilIdle()
        val result = vm.uiState.value.resultText
        vm.onEvent(AegisVaultUiEvent.UseResultAsInput)
        assertEquals(result, vm.uiState.value.inputText)
        assertEquals("", vm.uiState.value.resultText)
    }

    @Test fun failedActionClearsResultAndEmitsError() = runTest {
        val vm = vm(this)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("***"))
        vm.onEvent(AegisVaultUiEvent.Decode)
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.resultText)
        assertTrue(vm.uiState.value.message != null)
    }

    @Test fun onLeaveAppClearsOnlyWhenEnabled() = runTest {
        val repo = FakeRepo(AppSettings(autoClearOnLeave = false))
        val vm = AegisVaultViewModel(repo, StandardTestDispatcher(testScheduler))
        vm.onEvent(AegisVaultUiEvent.UpdateInput("abc"))
        vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        assertEquals("abc", vm.uiState.value.inputText)
        repo.update { it.copy(autoClearOnLeave = true) }
        advanceUntilIdle()
        vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        assertEquals("", vm.uiState.value.inputText)
    }


    @Test fun settingsUpdateChangesThemeLanguageProtectScreen() = runTest {
        val repo = FakeRepo()
        val vm = AegisVaultViewModel(repo, StandardTestDispatcher(testScheduler))
        vm.updateSettings { it.copy(protectScreen = true) }
        repo.update { it.copy(language = com.aegisvault.mobile.data.LanguageOption.ENGLISH, theme = com.aegisvault.mobile.data.ThemeOption.DARK, protectScreen = true) }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.settings.protectScreen)
        assertEquals(com.aegisvault.mobile.data.LanguageOption.ENGLISH, vm.uiState.value.settings.language)
        assertEquals(com.aegisvault.mobile.data.ThemeOption.DARK, vm.uiState.value.settings.theme)
    }

    private fun vm(scope: TestScope) = AegisVaultViewModel(FakeRepo(), StandardTestDispatcher(scope.testScheduler))
}
