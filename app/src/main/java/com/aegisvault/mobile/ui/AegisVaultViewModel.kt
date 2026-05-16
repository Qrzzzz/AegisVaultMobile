package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aegisvault.mobile.R
import com.aegisvault.mobile.core.AegisVaultEngine
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.ErrorCode
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

    init {
        viewModelScope.launch { repo.settings.collect { _uiState.update { s -> s.copy(settings = it) } } }
    }

    fun onEvent(event: AegisVaultUiEvent) {
        when (event) {
            is AegisVaultUiEvent.ChangeMode -> _uiState.update { it.copy(mode = event.mode, message = null) }
            is AegisVaultUiEvent.UpdateInput -> _uiState.update { it.copy(inputText = event.value) }
            is AegisVaultUiEvent.UpdatePassword -> _uiState.update { it.copy(password = event.value) }
            AegisVaultUiEvent.ClearSensitiveData -> clearSensitive()
            AegisVaultUiEvent.UseResultAsInput -> _uiState.update { it.copy(inputText = it.resultText, resultText = "", message = null) }
            AegisVaultUiEvent.OnLeaveApp -> if (_uiState.value.settings.autoClearOnLeave) clearSensitive()
            AegisVaultUiEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
            else -> runAction(event)
        }
    }

    private fun clearSensitive() {
        _uiState.update { it.copy(inputText = "", password = "", resultText = "", message = null, isBusy = false) }
    }

    private fun runAction(event: AegisVaultUiEvent) = viewModelScope.launch {
        _uiState.update { it.copy(isBusy = true, message = null) }
        val s = uiState.value
        val next = withContext(Dispatchers.Default) {
            try {
                when (event) {
                    AegisVaultUiEvent.Encrypt -> s.copy(isBusy = false, resultText = AegisVaultEngine.encryptText(s.inputText, s.password), message = UiMessage("RES:${R.string.status_text_encrypt_success}", MessageTone.SUCCESS))
                    AegisVaultUiEvent.Decrypt -> {
                        val r = AegisVaultEngine.decryptText(s.inputText, s.password)
                        s.copy(isBusy = false, resultText = r.plaintext, message = UiMessage("RES:${if (r.usedLegacyAk) R.string.status_text_decrypt_legacy_ak else R.string.status_text_decrypt_success}", if (r.usedLegacyAk) MessageTone.WARNING else MessageTone.SUCCESS))
                    }
                    AegisVaultUiEvent.Encode -> s.copy(isBusy = false, resultText = AegisVaultEngine.encodeText(s.inputText), message = UiMessage("RES:${R.string.status_base64_encode_success}", MessageTone.SUCCESS))
                    AegisVaultUiEvent.Decode -> s.copy(isBusy = false, resultText = AegisVaultEngine.decodeText(s.inputText), message = UiMessage("RES:${R.string.status_base64_decode_success}", MessageTone.SUCCESS))
                    else -> s.copy(isBusy = false)
                }
            } catch (e: AegisVaultException) {
                s.copy(isBusy = false, resultText = "", message = UiMessage("ERR:${errorRes(e.code)}", MessageTone.ERROR))
            }
        }
        _uiState.value = next
    }

    private fun errorRes(code: ErrorCode): Int = when (code) {
        ErrorCode.EMPTY_TEXT -> R.string.error_empty_text
        ErrorCode.EMPTY_PASSWORD -> R.string.error_empty_password
        ErrorCode.AUTH -> R.string.error_auth
        ErrorCode.BASE64_INVALID -> R.string.error_base64
        ErrorCode.UTF8_INVALID -> R.string.error_utf8
        ErrorCode.PROTOCOL -> R.string.error_protocol
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(transform) }

    class Factory(private val repo: AppPreferencesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AegisVaultViewModel(repo) as T
    }
}
