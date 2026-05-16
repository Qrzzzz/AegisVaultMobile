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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aegisvault.mobile.data.AppSettings
import com.aegisvault.mobile.data.ClipboardAutoClear
import kotlinx.coroutines.launch

@Composable
fun ModeSelector(mode: ToolMode, onSelect: (ToolMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        ToolMode.entries.forEachIndexed { i, m ->
            SegmentedButton(selected = m == mode, onClick = { onSelect(m) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(m.name) }
        }
    }
}

@Composable
fun SecureTextInputCard(value: String, onValue: (String) -> Unit, onPaste: () -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), label = { Text("Input") })
    TextButton(onClick = onPaste) { Text("Paste") }
}

@Composable
fun PasswordField(v: String, onV: (String) -> Unit, visible: Boolean, toggle: () -> Unit) {
    OutlinedTextField(value = v, onValueChange = onV, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
    TextButton(onClick = toggle) { Text(if (visible) "Hide" else "Show") }
}

@Composable
fun ActionButtons(mode: ToolMode, onEncrypt: () -> Unit, onDecrypt: () -> Unit, onEncode: () -> Unit, onDecode: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mode == ToolMode.AES) {
            Button(onClick = onEncrypt) { Text("Encrypt") }
            OutlinedButton(onClick = onDecrypt) { Text("Decrypt") }
        } else {
            Button(onClick = onEncode) { Text("Encode") }
            OutlinedButton(onClick = onDecode) { Text("Decode") }
        }
    }
}

@Composable
fun ResultCard(value: String, onCopy: suspend () -> Unit, onUseAsInput: () -> Unit) {
    val scope = rememberCoroutineScope()
    OutlinedTextField(value = value, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text("Result") })
    Button(onClick = onUseAsInput) { Text("Use result as input") }
    OutlinedButton(onClick = { scope.launch { onCopy() } }) { Text("Copy") }
}

@Composable
fun SettingsSheet(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Text("Protect screen")
    Switch(checked = settings.protectScreen, onCheckedChange = { checked -> onUpdate { it.copy(protectScreen = checked) } })
    Text("Auto-clear on leave")
    Switch(checked = settings.autoClearOnLeave, onCheckedChange = { checked -> onUpdate { it.copy(autoClearOnLeave = checked) } })
    TextButton(onClick = { onUpdate { it.copy(clipboardAutoClear = ClipboardAutoClear.S60) } }) { Text("Clipboard auto-clear 60s") }
}
