package com.aegisvault.mobile.ui

sealed interface AegisVaultUiEvent {
    data class ChangeMode(val mode: ToolMode) : AegisVaultUiEvent
    data class UpdateInput(val value: String) : AegisVaultUiEvent
    data class UpdatePassword(val value: String) : AegisVaultUiEvent
    data object Encrypt : AegisVaultUiEvent
    data object Decrypt : AegisVaultUiEvent
    data object Encode : AegisVaultUiEvent
    data object Decode : AegisVaultUiEvent
    data object ClearSensitiveData : AegisVaultUiEvent
    data object UseResultAsInput : AegisVaultUiEvent
    data object OnLeaveApp : AegisVaultUiEvent
    data object ClearMessage : AegisVaultUiEvent
}
