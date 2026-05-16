package com.aegisvault.mobile.ui

import com.aegisvault.mobile.data.AppSettings

enum class ToolMode { AES, BASE64 }
enum class MessageTone { SUCCESS, WARNING, ERROR }

data class UiMessage(val text: String, val tone: MessageTone)

data class AegisVaultUiState(
    val mode: ToolMode = ToolMode.AES,
    val inputText: String = "",
    val password: String = "",
    val resultText: String = "",
    val isBusy: Boolean = false,
    val message: UiMessage? = null,
    val settings: AppSettings = AppSettings(),
)
