package com.aegisvault.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aegisvault.mobile.R
import com.aegisvault.mobile.data.ClipboardAutoClear
import com.aegisvault.mobile.data.LanguageOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisVaultRoute(vm: AegisVaultViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var passwordVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner, state.settings.autoClearOnLeave) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.settings.language) {
        val tag = when (state.settings.language) { LanguageOption.SYSTEM -> ""; LanguageOption.ENGLISH -> "en"; LanguageOption.ZH_CN -> "zh-CN" }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(m.resId))
        vm.onEvent(AegisVaultUiEvent.ClearMessage)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.screen_title)) }, actions = {
            IconButton(onClick = { showSettings = true }) { Icon(androidx.compose.material.icons.Icons.Rounded.Info, contentDescription = stringResource(R.string.action_about)) }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeSelector(state.mode) { vm.onEvent(AegisVaultUiEvent.ChangeMode(it)) }
            SecureTextInputCard(state.inputText, { vm.onEvent(AegisVaultUiEvent.UpdateInput(it)) }, onPaste = {
                val txt = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (txt.isNullOrBlank()) scope.launch { snackbar.showSnackbar(context.getString(R.string.status_clipboard_empty)) }
                else vm.onEvent(AegisVaultUiEvent.UpdateInput(txt))
            })
            if (state.mode == ToolMode.AES) PasswordField(state.password, { vm.onEvent(AegisVaultUiEvent.UpdatePassword(it)) }, passwordVisible, { passwordVisible = !passwordVisible })
            else Text(stringResource(R.string.password_hint_base64))
            ActionButtons(state.mode, onEncrypt = { vm.onEvent(AegisVaultUiEvent.Encrypt) }, onDecrypt = { vm.onEvent(AegisVaultUiEvent.Decrypt) }, onEncode = { vm.onEvent(AegisVaultUiEvent.Encode) }, onDecode = { vm.onEvent(AegisVaultUiEvent.Decode) })
            ResultCard(state.resultText, onCopy = {
                if (state.resultText.isBlank()) { scope.launch { snackbar.showSnackbar(context.getString(R.string.error_copy_empty)) }; return@ResultCard }
                val copied = state.resultText
                clipboard.setPrimaryClip(ClipData.newPlainText("AegisVault Result", copied))
                scope.launch {
                    val res = snackbar.showSnackbar(context.getString(R.string.status_copied_security), actionLabel = context.getString(R.string.action_clear_clipboard))
                    if (res.name.contains("Action")) {
                        val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (shouldClearClipboard(current, copied)) clipboard.setPrimaryClip(ClipData.newPlainText("AegisVault", ""))
                    }
                }
                val secs = state.settings.clipboardAutoClear.seconds
                if (secs != null) scope.launch {
                    delay(secs * 1000)
                    val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (shouldClearClipboard(current, copied)) clipboard.setPrimaryClip(ClipData.newPlainText("AegisVault", ""))
                }
            }, onUseAsInput = { vm.onEvent(AegisVaultUiEvent.UseResultAsInput) })
            androidx.compose.material3.OutlinedButton(onClick = { vm.onEvent(AegisVaultUiEvent.ClearSensitiveData); focusManager.clearFocus() }) { Text(stringResource(R.string.action_clear_sensitive_data)) }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(state.settings, onUpdate = vm::updateSettings)
        }
    }
}


internal fun shouldClearClipboard(current: String?, copied: String): Boolean = current == copied
