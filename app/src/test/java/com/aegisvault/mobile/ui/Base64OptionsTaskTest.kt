package com.aegisvault.mobile.ui

import com.aegisvault.mobile.core.Base64DecodeOptions
import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.AppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Base64OptionsTaskTest {
    @get:Rule val main = MainDispatcherRule()
    private class Repo : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }

    @Test fun viewModelUsesBothParametersPreservesWhitespaceResultAndEmptySuccess() = runTest {
        val vm = AegisVaultViewModel(Repo(), StandardTestDispatcher(testScheduler))
        vm.onEvent(AegisVaultUiEvent.UpdateInput(" YQ== "))
        vm.onEvent(AegisVaultUiEvent.Decode); advanceUntilIdle()
        assertEquals(MessageTone.ERROR, vm.uiState.value.message!!.tone)
        vm.onEvent(AegisVaultUiEvent.UpdateBase64Options(Base64DecodeOptions(true, true)))
        vm.onEvent(AegisVaultUiEvent.Decode); advanceUntilIdle()
        assertEquals(MessageTone.ERROR, vm.uiState.value.message!!.tone)
        vm.onEvent(AegisVaultUiEvent.UpdateBase64Options(Base64DecodeOptions(false, true)))
        vm.onEvent(AegisVaultUiEvent.Decode); advanceUntilIdle()
        assertEquals("a", vm.uiState.value.resultText)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("IAkK"))
        vm.onEvent(AegisVaultUiEvent.Decode); advanceUntilIdle()
        assertEquals(" \t\n", vm.uiState.value.resultText)
        vm.onEvent(AegisVaultUiEvent.UseResultAsInput)
        assertEquals(" \t\n", vm.uiState.value.inputText)
        vm.onEvent(AegisVaultUiEvent.UpdateInput("")); vm.onEvent(AegisVaultUiEvent.Encode); advanceUntilIdle()
        assertEquals("", vm.uiState.value.resultText)
        assertEquals(MessageTone.SUCCESS, vm.uiState.value.message!!.tone)
        vm.onEvent(AegisVaultUiEvent.ClearSensitiveData)
        assertEquals(Base64DecodeOptions(false, true), vm.uiState.value.base64Options)
    }
}
