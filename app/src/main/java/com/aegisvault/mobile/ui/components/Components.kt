@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aegisvault.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
fun ModeSelector(mode: ToolMode, onSelect: (ToolMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ToolMode.entries.forEachIndexed { index, item ->
            SegmentedButton(selected = mode == item, onClick = { onSelect(item) }, shape = SegmentedButtonDefaults.itemShape(index, ToolMode.entries.size)) {
                Text(if (item == ToolMode.AES) stringResource(R.string.tool_aes) else stringResource(R.string.tool_base64))
            }
        }
    }
}

@Composable
fun SecureTextInputCard(value: String, onValue: (String) -> Unit, onPaste: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                label = { Text(stringResource(R.string.label_input)) },
                placeholder = { Text(stringResource(R.string.hint_input)) },
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                    }
                }
            )
            Text(stringResource(R.string.input_supporting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PasswordField(v: String, onV: (String) -> Unit, visible: Boolean, toggle: () -> Unit) {
    OutlinedTextField(
        value = v,
        onValueChange = onV,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.label_password)) },
        placeholder = { Text(stringResource(R.string.hint_password)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = toggle) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.content_description_toggle_password)
                )
            }
        }
    )
}

@Composable
fun ActionButtons(mode: ToolMode, onEncrypt: () -> Unit, onDecrypt: () -> Unit, onEncode: () -> Unit, onDecode: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (mode == ToolMode.AES) {
            Button(onClick = onEncrypt, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_encrypt)) }
            FilledTonalButton(onClick = onDecrypt, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_decrypt)) }
        } else {
            Button(onClick = onEncode, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_encode)) }
            FilledTonalButton(onClick = onDecode, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_decode)) }
        }
    }
}

@Composable
fun ResultCard(value: String, onCopy: suspend () -> Unit, onUseAsInput: () -> Unit, onClearResult: () -> Unit) {
    val scope = rememberCoroutineScope()
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.label_result), style = MaterialTheme.typography.titleMedium)
            if (value.isBlank()) {
                Text(stringResource(R.string.result_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    text = value,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { scope.launch { onCopy() } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_copy_result)) }
                OutlinedButton(onClick = onUseAsInput, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_use_result_as_input)) }
            }
            OutlinedButton(onClick = onClearResult, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_clear_result)) }
        }
    }
}

@Composable
fun SettingsSheet(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        SettingsSection(stringResource(R.string.settings_group_appearance))
        ListItem(headlineContent = { Text(stringResource(R.string.settings_language)) }, supportingContent = { Text(stringResource(R.string.settings_language_support)) })
        LanguageOption.entries.forEach { option ->
            ListItem(headlineContent = { Text(languageLabel(option)) }, leadingContent = { RadioButton(selected = settings.language == option, onClick = null) }, modifier = Modifier.clickable { onUpdate { it.copy(language = option) } })
        }
        ListItem(headlineContent = { Text(stringResource(R.string.settings_theme)) }, supportingContent = { Text(stringResource(R.string.settings_theme_support)) })
        ThemeOption.entries.forEach { option ->
            ListItem(headlineContent = { Text(themeLabel(option)) }, leadingContent = { RadioButton(selected = settings.theme == option, onClick = null) }, modifier = Modifier.clickable { onUpdate { it.copy(theme = option) } })
        }

        SettingsSection(stringResource(R.string.settings_group_security))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_protect_screen)) },
            supportingContent = { Text(stringResource(R.string.settings_protect_screen_support)) },
            trailingContent = { Switch(checked = settings.protectScreen, onCheckedChange = null) },
            modifier = Modifier.clickable { onUpdate { it.copy(protectScreen = !settings.protectScreen) } },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_auto_clear_leave)) },
            supportingContent = { Text(stringResource(R.string.settings_auto_clear_leave_support)) },
            trailingContent = { Switch(checked = settings.autoClearOnLeave, onCheckedChange = null) },
            modifier = Modifier.clickable { onUpdate { it.copy(autoClearOnLeave = !settings.autoClearOnLeave) } },
        )

        SettingsSection(stringResource(R.string.settings_group_clipboard))
        ListItem(headlineContent = { Text(stringResource(R.string.settings_clipboard_auto_clear)) }, supportingContent = { Text(stringResource(R.string.settings_clipboard_auto_clear_support)) })
        ClipboardAutoClear.entries.forEach { option ->
            ListItem(headlineContent = { Text(clipboardLabel(option)) }, leadingContent = { RadioButton(selected = settings.clipboardAutoClear == option, onClick = null) }, modifier = Modifier.clickable { onUpdate { it.copy(clipboardAutoClear = option) } })
        }

        SettingsSection(stringResource(R.string.settings_group_about))
        ListItem(headlineContent = { Text(stringResource(R.string.about_title)) }, supportingContent = { Text(stringResource(R.string.about_body)) })
    }
}

@Composable private fun SettingsSection(title: String) { Surface(tonalElevation = 1.dp) { Text(title, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall); HorizontalDivider() } }
@Composable private fun languageLabel(option: LanguageOption): String = when (option) { LanguageOption.SYSTEM -> stringResource(R.string.language_system); LanguageOption.ENGLISH -> stringResource(R.string.language_en); LanguageOption.ZH_CN -> stringResource(R.string.language_zh_cn) }
@Composable private fun themeLabel(option: ThemeOption): String = when (option) { ThemeOption.SYSTEM -> stringResource(R.string.theme_system); ThemeOption.LIGHT -> stringResource(R.string.theme_light); ThemeOption.DARK -> stringResource(R.string.theme_dark) }
@Composable private fun clipboardLabel(option: ClipboardAutoClear): String = when (option) { ClipboardAutoClear.OFF -> stringResource(R.string.clipboard_off); ClipboardAutoClear.S30 -> stringResource(R.string.clipboard_30s); ClipboardAutoClear.S60 -> stringResource(R.string.clipboard_60s); ClipboardAutoClear.S300 -> stringResource(R.string.clipboard_300s) }
