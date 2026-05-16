package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aegisvault.mobile.core.AegisVaultEngine
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.data.AppPreferencesRepository
import com.aegisvault.mobile.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AegisVaultViewModel(private val repo: AppPreferencesRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AegisVaultUiState())
    val uiState: StateFlow<AegisVaultUiState> = _uiState.asStateFlow()

    init { viewModelScope.launch { repo.settings.collect { _uiState.update { s -> s.copy(settings = it) } } } }

    fun onEvent(event: AegisVaultUiEvent) {
        when (event) {
            is AegisVaultUiEvent.ChangeMode -> _uiState.update { it.copy(mode = event.mode) }
            is AegisVaultUiEvent.UpdateInput -> _uiState.update { it.copy(inputText = event.value) }
            is AegisVaultUiEvent.UpdatePassword -> _uiState.update { it.copy(password = event.value) }
            AegisVaultUiEvent.ClearSensitiveData -> _uiState.update { it.copy(inputText = "", password = "", resultText = "", message = null, isWarning = false) }
            AegisVaultUiEvent.UseResultAsInput -> _uiState.update { it.copy(inputText = it.resultText, resultText = "") }
            else -> runAction(event)
        }
    }

    private fun runAction(event: AegisVaultUiEvent) = viewModelScope.launch {
        _uiState.update { it.copy(isBusy = true, message = null) }
        val s = uiState.value
        val result = withContext(Dispatchers.Default) {
            try {
                when (event) {
                    AegisVaultUiEvent.Encrypt -> Triple(AegisVaultEngine.encryptText(s.inputText, s.password), "Encrypted", false)
                    AegisVaultUiEvent.Decrypt -> {
                        val r = AegisVaultEngine.decryptText(s.inputText, s.password)
                        Triple(r.plaintext, if (r.usedLegacyAk) "Decrypted with legacy AK mode" else "Decrypted", r.usedLegacyAk)
                    }
                    AegisVaultUiEvent.Encode -> Triple(AegisVaultEngine.encodeText(s.inputText), "Encoded", false)
                    AegisVaultUiEvent.Decode -> Triple(AegisVaultEngine.decodeText(s.inputText), "Decoded", false)
                    else -> Triple("", "", false)
                }
            } catch (e: AegisVaultException) {
                Triple("", e.code.name, true)
            }
        }
        _uiState.update { it.copy(isBusy = false, resultText = result.first.ifBlank { it.resultText }, message = result.second, isWarning = result.third) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(transform) }

    class Factory(private val repo: AppPreferencesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AegisVaultViewModel(repo) as T
    }
}
