package com.aegisvault.mobile.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aegisvault.mobile.R
import com.aegisvault.mobile.data.SettingsNotice
import com.aegisvault.mobile.update.UpdateViewModel
import com.aegisvault.mobile.ui.components.UpdateSection
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisVaultRoute(vm: AegisVaultViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val updateVm: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory(context.applicationContext))
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val clipboardCleanup = remember(context) { AppClipboard.get(context) }
    val locales = remember(context) { AppLocaleBridge(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var passwordVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val languageReady by rememberUpdatedState(state.settingsLoaded && state.settingsNotice != SettingsNotice.READ_RETRYING)
    val currentLanguage by rememberUpdatedState(state.settings.language)

    DisposableEffect(lifecycleOwner, vm, context, locales) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && context.activity()?.isChangingConfigurations != true)
                vm.onEvent(AegisVaultUiEvent.OnLeaveApp)
            if (event == Lifecycle.Event.ON_RESUME && languageReady)
                locales.synchronize(currentLanguage) { language -> vm.updateSettings { it.copy(language = language) } }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(languageReady, state.settings.language) {
        if (languageReady) locales.synchronize(state.settings.language) { language -> vm.updateSettings { it.copy(language = language) } }
    }

    LaunchedEffect(state.settingsLoaded, state.settings.clipboardAutoClear) {
        if (state.settingsLoaded) clipboardCleanup.setTimeout(state.settings.clipboardAutoClear.seconds)
    }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(m.resId))
        vm.onEvent(AegisVaultUiEvent.ClearMessage)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.screen_title)) }, actions = {
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.settingsNotice?.let { notice ->
                Text(stringResource(when (notice) {
                    SettingsNotice.VALUES_RECOVERED -> R.string.settings_values_recovered
                    SettingsNotice.STORE_RESET -> R.string.settings_store_reset
                    SettingsNotice.READ_RETRYING -> R.string.settings_read_retrying
                    SettingsNotice.READ_RECOVERED -> R.string.settings_read_recovered
                    SettingsNotice.WRITE_FAILED -> R.string.settings_write_failed
                }))
                TextButton(onClick = { vm.onEvent(AegisVaultUiEvent.AcknowledgeSettingsNotice) }) {
                    Text(stringResource(R.string.settings_acknowledge))
                }
            }
            ModeSelector(state.mode) { vm.onEvent(AegisVaultUiEvent.ChangeMode(it)) }
            SecureTextInputCard(state.inputText, { vm.onEvent(AegisVaultUiEvent.UpdateInput(it)) }, onPaste = {
                val txt = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (txt == null) scope.launch { snackbar.showSnackbar(context.getString(R.string.status_clipboard_empty)) }
                else vm.onEvent(AegisVaultUiEvent.UpdateInput(txt))
            })
            if (state.mode == ToolMode.AES) {
                PasswordField(state.password, { vm.onEvent(AegisVaultUiEvent.UpdatePassword(it)) }, passwordVisible, { passwordVisible = !passwordVisible })
                Text(stringResource(R.string.password_hint_aes))
            } else {
                Text(stringResource(R.string.password_hint_base64))
                Base64OptionsControls(state.base64Options) { vm.onEvent(AegisVaultUiEvent.UpdateBase64Options(it)) }
            }
            ActionButtons(state.mode, onEncrypt = { vm.onEvent(AegisVaultUiEvent.Encrypt) }, onDecrypt = { vm.onEvent(AegisVaultUiEvent.Decrypt) }, onEncode = { vm.onEvent(AegisVaultUiEvent.Encode) }, onDecode = { vm.onEvent(AegisVaultUiEvent.Decode) })
            ResultCard(state.resultText, onCopy = {
                if (state.resultText.isEmpty()) { scope.launch { snackbar.showSnackbar(context.getString(R.string.error_copy_empty)) }; return@ResultCard }
                val copied = clipboardCleanup.copy(state.resultText)
                scope.launch {
                    val res = snackbar.showSnackbar(context.getString(R.string.status_copied_security), actionLabel = context.getString(R.string.action_clear_clipboard))
                    if (res.name.contains("Action")) {
                        clipboardCleanup.clear(copied)
                    }
                }
            }, onUseAsInput = { vm.onEvent(AegisVaultUiEvent.UseResultAsInput) }, onClearResult = { vm.onEvent(AegisVaultUiEvent.ClearResult) })
            OutlinedButton(onClick = { vm.onEvent(AegisVaultUiEvent.ClearSensitiveData); focusManager.clearFocus() }) { Text(stringResource(R.string.action_clear_sensitive_data)) }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(state.settings, onUpdate = { transform ->
                val updated = transform(state.settings)
                if (updated.language != state.settings.language) locales.select(updated.language)
                vm.updateSettings(transform)
            }, updateContent = { UpdateSection(updateVm) })
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
