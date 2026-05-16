package com.aegisvault.mobile.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisVaultRoute(vm: AegisVaultViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("AegisVault") }) }, snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeSelector(state.mode) { vm.onEvent(AegisVaultUiEvent.ChangeMode(it)) }
            SecureTextInputCard(state.inputText, { vm.onEvent(AegisVaultUiEvent.UpdateInput(it)) }, onPaste = {})
            if (state.mode == ToolMode.AES) {
                PasswordField(state.password, { vm.onEvent(AegisVaultUiEvent.UpdatePassword(it)) }, passwordVisible, { passwordVisible = !passwordVisible })
            } else Text("Base64 is not encryption", style = MaterialTheme.typography.bodySmall)
            ActionButtons(state.mode, onEncrypt = { vm.onEvent(AegisVaultUiEvent.Encrypt) }, onDecrypt = { vm.onEvent(AegisVaultUiEvent.Decrypt) }, onEncode = { vm.onEvent(AegisVaultUiEvent.Encode) }, onDecode = { vm.onEvent(AegisVaultUiEvent.Decode) })
            ResultCard(state.resultText, onCopy = {
                if (state.resultText.isBlank()) snackbar.showSnackbar("Nothing to copy")
                else {
                    clipboard.setClip(ClipData.newPlainText("result", state.resultText))
                    val res = snackbar.showSnackbar("Copied. Clipboard may be visible to system.", actionLabel = "Clear clipboard")
                    if (res.name.contains("Action")) clipboard.setClip(ClipData.newPlainText("", ""))
                }
            }, onUseAsInput = { vm.onEvent(AegisVaultUiEvent.UseResultAsInput) })
            OutlinedButton(modifier=Modifier.testTag("clearSensitive"),onClick = { vm.onEvent(AegisVaultUiEvent.ClearSensitiveData); focusManager.clearFocus() }) { Text("Clear sensitive data") }
            SettingsSheet(state.settings, onUpdate = vm::updateSettings)
        }
    }
}
