package com.aegisvault.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aegisvault.mobile.R
import com.aegisvault.mobile.core.AegisVaultEngine
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.Base64DecodeOptions
import com.aegisvault.mobile.core.ErrorCode
import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AegisVaultViewModel(
    private val repo: AppSettingsRepository,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val operation: suspend (AegisVaultUiEvent, String, String, Base64DecodeOptions) -> ActionResult = ::executeAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AegisVaultUiState())
    val uiState: StateFlow<AegisVaultUiState> = _uiState.asStateFlow()

    private var generation = 0L
    private var activeJob: Job? = null
    private var destroyed = false

    init {
        viewModelScope.launch { repo.settings.collect { _uiState.update { s -> s.copy(settings = it, settingsLoaded = true) } } }
        viewModelScope.launch { repo.notices.collect { _uiState.update { s -> s.copy(settingsNotice = it) } } }
    }

    fun onEvent(event: AegisVaultUiEvent) {
        if (destroyed) return
        if (event is AegisVaultUiEvent.UpdateInput || event is AegisVaultUiEvent.UpdatePassword ||
            event is AegisVaultUiEvent.ChangeMode || event is AegisVaultUiEvent.UpdateBase64Options || event == AegisVaultUiEvent.UseResultAsInput ||
            event == AegisVaultUiEvent.ClearResult) invalidateTask()
        when (event) {
            is AegisVaultUiEvent.ChangeMode -> _uiState.update { it.copy(mode = event.mode, message = null) }
            is AegisVaultUiEvent.UpdateBase64Options -> _uiState.update { it.copy(base64Options = event.value) }
            is AegisVaultUiEvent.UpdateInput -> _uiState.update { it.copy(inputText = event.value) }
            is AegisVaultUiEvent.UpdatePassword -> _uiState.update { it.copy(password = event.value) }
            AegisVaultUiEvent.ClearSensitiveData -> clearSensitive()
            AegisVaultUiEvent.UseResultAsInput -> _uiState.update { it.copy(inputText = it.resultText, resultText = "", message = null) }
            AegisVaultUiEvent.ClearResult -> _uiState.update { it.copy(resultText = "", message = null) }
            AegisVaultUiEvent.OnLeaveApp -> if (!_uiState.value.settingsLoaded || _uiState.value.settings.autoClearOnLeave) clearSensitive()
            AegisVaultUiEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
            AegisVaultUiEvent.AcknowledgeSettingsNotice -> {
                repo.acknowledgeNotice()
                _uiState.update { it.copy(settingsNotice = null) }
            }
            else -> runAction(event)
        }
    }

    private fun invalidateTask() {
        generation++
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(isBusy = false) }
    }

    override fun onCleared() {
        destroyed = true
        clearSensitive()
        super.onCleared()
    }

    private fun clearSensitive() {
        invalidateTask()
        _uiState.update { it.copy(inputText = "", password = "", resultText = "", message = null, isBusy = false) }
    }

    private fun runAction(event: AegisVaultUiEvent) {
        // Reserve synchronously: two taps before launch dispatch cannot start two jobs.
        if (_uiState.value.isBusy || destroyed) return
        val id = ++generation
        val snapshot = uiState.value
        _uiState.update { it.copy(isBusy = true, message = null) }
        activeJob = viewModelScope.launch {
            try {
                val result = withContext(workerDispatcher) { operation(event, snapshot.inputText, snapshot.password, snapshot.base64Options) }
                if (id == generation && !destroyed) _uiState.update {
                    it.copy(resultText = result.text, message = result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AegisVaultException) {
                if (id == generation && !destroyed) _uiState.update {
                    it.copy(resultText = "", message = UiMessage(errorRes(e.code), MessageTone.ERROR))
                }
            } catch (_: java.security.GeneralSecurityException) {
                if (id == generation && !destroyed) _uiState.update {
                    it.copy(resultText = "", message = UiMessage(R.string.error_internal, MessageTone.ERROR))
                }
            } finally {
                if (id == generation && !destroyed) {
                    activeJob = null
                    _uiState.update { it.copy(isBusy = false) }
                }
            }
        }
    }

    data class ActionResult(val text: String, val message: UiMessage)

    companion object {
        private suspend fun executeAction(event: AegisVaultUiEvent, input: String, password: String, options: Base64DecodeOptions): ActionResult =
            when (event) {
                AegisVaultUiEvent.Encrypt -> ActionResult(AegisVaultEngine.encryptText(input, password), UiMessage(R.string.status_text_encrypt_success, MessageTone.SUCCESS))
                AegisVaultUiEvent.Decrypt -> {
                    val r = AegisVaultEngine.decryptText(input, password)
                    ActionResult(r.plaintext, UiMessage(R.string.status_text_decrypt_success, MessageTone.SUCCESS))
                }
                AegisVaultUiEvent.Encode -> ActionResult(AegisVaultEngine.encodeText(input), UiMessage(R.string.status_base64_encode_success, MessageTone.SUCCESS))
                AegisVaultUiEvent.Decode -> ActionResult(AegisVaultEngine.decodeText(input, options.strict, options.ignoreAsciiWhitespace), UiMessage(R.string.status_base64_decode_success, MessageTone.SUCCESS))
                else -> error("Not an operation")
            }
    }

    private fun errorRes(code: ErrorCode): Int = when (code) {
        ErrorCode.EMPTY_TEXT -> R.string.error_empty_text
        ErrorCode.INPUT_INVALID -> R.string.error_input_unicode
        ErrorCode.EMPTY_PASSWORD -> R.string.error_empty_password
        ErrorCode.AUTH -> R.string.error_auth
        ErrorCode.BASE64_INVALID -> R.string.error_base64
        ErrorCode.UTF8_INVALID -> R.string.error_utf8
        ErrorCode.PROTOCOL -> R.string.error_protocol
        ErrorCode.MIGRATION -> R.string.error_migration
        ErrorCode.RESOURCE -> R.string.error_resource
        ErrorCode.INTERNAL -> R.string.error_internal
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        try { repo.update(transform) }
        catch (_: java.io.IOException) {
            _uiState.update { it.copy(settingsNotice = com.aegisvault.mobile.data.SettingsNotice.WRITE_FAILED) }
        }
    }

    class Factory(private val repo: AppSettingsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AegisVaultViewModel(repo) as T
    }
}
