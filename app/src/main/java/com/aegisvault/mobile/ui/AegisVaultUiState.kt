package com.aegisvault.mobile.ui

import com.aegisvault.mobile.core.Base64DecodeOptions
import androidx.annotation.StringRes
import com.aegisvault.mobile.data.AppSettings

enum class ToolMode { AES, BASE64 }
enum class MessageTone { SUCCESS, WARNING, ERROR }

data class UiMessage(@StringRes val resId: Int, val tone: MessageTone)

data class AegisVaultUiState(
    val mode: ToolMode = ToolMode.AES,
    val base64Options: Base64DecodeOptions = Base64DecodeOptions(),
    val inputText: String = "",
    val password: String = "",
    val resultText: String = "",
    val isBusy: Boolean = false,
    val message: UiMessage? = null,
    val settings: AppSettings = AppSettings(),
)
