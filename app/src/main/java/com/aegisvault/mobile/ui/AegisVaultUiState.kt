package com.aegisvault.mobile.ui

import com.aegisvault.mobile.data.AppSettings

enum class ToolMode { AES, BASE64 }

data class AegisVaultUiState(
    val mode: ToolMode = ToolMode.AES,
    val inputText: String = "",
    val password: String = "",
    val resultText: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    val isWarning: Boolean = false,
    val settings: AppSettings = AppSettings(),
)
