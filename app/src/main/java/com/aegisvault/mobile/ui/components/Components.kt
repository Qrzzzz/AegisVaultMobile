package com.aegisvault.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aegisvault.mobile.R
import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.ClipboardAutoClear
import com.aegisvault.mobile.data.LanguageOption
import com.aegisvault.mobile.data.ThemeOption
import kotlinx.coroutines.launch

@Composable
fun ModeSelector(mode: ToolMode, onSelect: (ToolMode) -> Unit) { SingleChoiceSegmentedButtonRow { ToolMode.entries.forEachIndexed { i, m -> SegmentedButton(selected = m == mode, onClick = { onSelect(m) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (m == ToolMode.AES) stringResource(R.string.tool_aes) else stringResource(R.string.tool_base64)) } } } }
@Composable
fun SecureTextInputCard(value: String, onValue: (String) -> Unit, onPaste: () -> Unit) { OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_input)) }); TextButton(onClick = onPaste) { Text(stringResource(R.string.action_paste)) } }
@Composable
fun PasswordField(v: String, onV: (String) -> Unit, visible: Boolean, toggle: () -> Unit) { OutlinedTextField(value = v, onValueChange = onV, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_password)) }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)); TextButton(onClick = toggle) { Text(if (visible) stringResource(R.string.action_hide) else stringResource(R.string.action_show)) } }
@Composable
fun ActionButtons(mode: ToolMode, onEncrypt: () -> Unit, onDecrypt: () -> Unit, onEncode: () -> Unit, onDecode: () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (mode == ToolMode.AES) { Button(onClick = onEncrypt) { Text(stringResource(R.string.action_encrypt)) }; OutlinedButton(onClick = onDecrypt) { Text(stringResource(R.string.action_decrypt)) } } else { Button(onClick = onEncode) { Text(stringResource(R.string.action_encode)) }; OutlinedButton(onClick = onDecode) { Text(stringResource(R.string.action_decode)) } } } }
@Composable
fun ResultCard(value: String, onCopy: suspend () -> Unit, onUseAsInput: () -> Unit) { val scope = rememberCoroutineScope(); OutlinedTextField(value = value, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_result)) }); Button(onClick = onUseAsInput) { Text(stringResource(R.string.action_use_result_as_input)) }; OutlinedButton(onClick = { scope.launch { onCopy() } }) { Text(stringResource(R.string.action_copy_result)) } }
@Composable
fun SettingsSheet(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_title))
        Text(stringResource(R.string.settings_language))
        TextButton(onClick = { onUpdate { it.copy(language = LanguageOption.SYSTEM) } }) { Text(stringResource(R.string.language_system)) }
        TextButton(onClick = { onUpdate { it.copy(language = LanguageOption.ENGLISH) } }) { Text(stringResource(R.string.language_en)) }
        TextButton(onClick = { onUpdate { it.copy(language = LanguageOption.ZH_CN) } }) { Text(stringResource(R.string.language_zh_cn)) }
        Text(stringResource(R.string.settings_theme))
        TextButton(onClick = { onUpdate { it.copy(theme = ThemeOption.SYSTEM) } }) { Text(stringResource(R.string.theme_system)) }
        TextButton(onClick = { onUpdate { it.copy(theme = ThemeOption.LIGHT) } }) { Text(stringResource(R.string.theme_light)) }
        TextButton(onClick = { onUpdate { it.copy(theme = ThemeOption.DARK) } }) { Text(stringResource(R.string.theme_dark)) }
        Text(stringResource(R.string.settings_protect_screen)); Switch(checked = settings.protectScreen, onCheckedChange = { checked -> onUpdate { it.copy(protectScreen = checked) } })
        Text(stringResource(R.string.settings_auto_clear_leave)); Switch(checked = settings.autoClearOnLeave, onCheckedChange = { checked -> onUpdate { it.copy(autoClearOnLeave = checked) } })
        Text(stringResource(R.string.settings_clipboard_auto_clear))
        TextButton(onClick = { onUpdate { it.copy(clipboardAutoClear = ClipboardAutoClear.OFF) } }) { Text(stringResource(R.string.clipboard_off)) }
        TextButton(onClick = { onUpdate { it.copy(clipboardAutoClear = ClipboardAutoClear.S30) } }) { Text(stringResource(R.string.clipboard_30s)) }
        TextButton(onClick = { onUpdate { it.copy(clipboardAutoClear = ClipboardAutoClear.S60) } }) { Text(stringResource(R.string.clipboard_60s)) }
        TextButton(onClick = { onUpdate { it.copy(clipboardAutoClear = ClipboardAutoClear.S300) } }) { Text(stringResource(R.string.clipboard_300s)) }
        Text(stringResource(R.string.about_body))
    }
}
