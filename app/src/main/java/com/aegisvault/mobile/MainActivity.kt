package com.aegisvault.mobile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aegisvault.mobile.core.AegisVaultEngine
import com.aegisvault.mobile.core.AegisVaultException
import com.aegisvault.mobile.core.ErrorCode
import com.aegisvault.mobile.data.AppLanguage
import com.aegisvault.mobile.data.AppPreferences
import com.aegisvault.mobile.ui.theme.AegisVaultTheme
import com.aegisvault.mobile.ui.theme.AnimatedAuroraBackground
import com.aegisvault.mobile.ui.theme.rememberAegisVaultUiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = AppPreferences(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AegisVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(
                        activity = this,
                        preferences = preferences,
                    )
                }
            }
        }
    }
}

private enum class TextTool { AES, BASE64 }

private enum class Tone { READY, SUCCESS, WARNING, ERROR, PROCESSING }

private data class StatusMessage(
    val text: String,
    val tone: Tone,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    activity: Activity,
    preferences: AppPreferences,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val coroutineScope = rememberCoroutineScope()
    val uiPalette = rememberAegisVaultUiPalette()

    var textTool by rememberSaveable { mutableStateOf(TextTool.AES) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var resultText by rememberSaveable { mutableStateOf("") }
    var isBusy by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var preferenceState by remember { mutableStateOf(preferences.load()) }
    var status by remember {
        mutableStateOf(StatusMessage(context.getString(R.string.status_ready), Tone.READY))
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(text = stringResource(R.string.action_close))
                }
            },
            title = { Text(text = stringResource(R.string.about_title)) },
            text = { Text(text = stringResource(R.string.about_body)) },
        )
    }

    AnimatedAuroraBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(uiPalette.pageScrim),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.screen_title),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.screen_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAboutDialog = true }, enabled = !isBusy) {
                            Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.action_about))
                        }
                        TextButton(
                            onClick = {
                                inputText = ""
                                password = ""
                                resultText = ""
                                focusManager.clearFocus()
                                status = StatusMessage(context.getString(R.string.status_ready), Tone.READY)
                            },
                            enabled = !isBusy,
                        ) {
                            Text(text = stringResource(R.string.action_clear_all))
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HeroCard(uiPalette = uiPalette)
                }

                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = uiPalette.strongCardSurface),
                        border = BorderStroke(1.dp, uiPalette.cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ToolModeSelector(
                                selected = textTool,
                                uiPalette = uiPalette,
                                onSelected = {
                                    textTool = it
                                    resultText = ""
                                    focusManager.clearFocus()
                                    status = StatusMessage(context.getString(R.string.status_ready), Tone.READY)
                                },
                            )

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                label = { Text(text = stringResource(R.string.label_input)) },
                                placeholder = { Text(text = stringResource(R.string.hint_input)) },
                                shape = RoundedCornerShape(22.dp),
                            )

                            AssistChip(
                                onClick = {
                                    val clipText = clipboardManager.primaryClip
                                        ?.getItemAt(0)
                                        ?.coerceToText(context)
                                        ?.toString()
                                        .orEmpty()
                                    if (clipText.isNotEmpty()) {
                                        inputText = clipText
                                    }
                                },
                                label = { Text(text = stringResource(R.string.action_paste)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.ContentPaste,
                                        contentDescription = stringResource(R.string.action_paste),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = uiPalette.chipSurface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = null,
                            )

                            if (textTool == TextTool.AES) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                            .replace("\r", "")
                                            .replace("\n", "")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(text = stringResource(R.string.label_password)) },
                                    placeholder = { Text(text = stringResource(R.string.hint_password)) },
                                    shape = RoundedCornerShape(22.dp),
                                    singleLine = true,
                                    maxLines = 1,
                                    visualTransformation = if (passwordVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.None,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() },
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) {
                                                    Icons.Rounded.VisibilityOff
                                                } else {
                                                    Icons.Rounded.Visibility
                                                },
                                                contentDescription = stringResource(
                                                    R.string.content_description_toggle_password,
                                                ),
                                            )
                                        }
                                    },
                                )
                                Text(
                                    text = stringResource(R.string.password_hint_aes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.password_hint_base64),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SoftActionButton(
                                    text = stringResource(
                                        if (textTool == TextTool.AES) R.string.action_encrypt else R.string.action_encode,
                                    ),
                                    containerColor = uiPalette.encodeButton,
                                    contentColor = uiPalette.encodeButtonText,
                                    enabled = !isBusy,
                                    onClick = {
                                        coroutineScope.launch {
                                            isBusy = true
                                            status = StatusMessage(
                                                context.getString(R.string.status_processing),
                                                Tone.PROCESSING,
                                            )
                                            focusManager.clearFocus()
                                            try {
                                                val output = withContext(Dispatchers.IO) {
                                                    if (textTool == TextTool.AES) {
                                                        AegisVaultEngine.encryptText(inputText, password)
                                                    } else {
                                                        AegisVaultEngine.encodeText(inputText)
                                                    }
                                                }
                                                resultText = output
                                                status = StatusMessage(
                                                    context.getString(
                                                        if (textTool == TextTool.AES) {
                                                            R.string.status_text_encrypt_success
                                                        } else {
                                                            R.string.status_base64_encode_success
                                                        },
                                                    ),
                                                    Tone.SUCCESS,
                                                )
                                            } catch (error: AegisVaultException) {
                                                status = StatusMessage(errorMessage(context, error), Tone.ERROR)
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    },
                                )

                                SoftActionButton(
                                    text = stringResource(
                                        if (textTool == TextTool.AES) R.string.action_decrypt else R.string.action_decode,
                                    ),
                                    containerColor = uiPalette.decodeButton,
                                    contentColor = uiPalette.decodeButtonText,
                                    enabled = !isBusy,
                                    onClick = {
                                        coroutineScope.launch {
                                            isBusy = true
                                            status = StatusMessage(
                                                context.getString(R.string.status_processing),
                                                Tone.PROCESSING,
                                            )
                                            focusManager.clearFocus()
                                            try {
                                                if (textTool == TextTool.AES) {
                                                    val textOutput = withContext(Dispatchers.IO) {
                                                        AegisVaultEngine.decryptText(inputText, password)
                                                    }
                                                    resultText = textOutput.plaintext
                                                    status = StatusMessage(
                                                        context.getString(
                                                            if (textOutput.usedLegacyAk) {
                                                                R.string.status_text_decrypt_legacy_ak
                                                            } else {
                                                                R.string.status_text_decrypt_success
                                                            },
                                                        ),
                                                        if (textOutput.usedLegacyAk) Tone.WARNING else Tone.SUCCESS,
                                                    )
                                                } else {
                                                    resultText = withContext(Dispatchers.IO) {
                                                        AegisVaultEngine.decodeText(inputText)
                                                    }
                                                    status = StatusMessage(
                                                        context.getString(R.string.status_base64_decode_success),
                                                        Tone.SUCCESS,
                                                    )
                                                }
                                            } catch (error: AegisVaultException) {
                                                status = StatusMessage(errorMessage(context, error), Tone.ERROR)
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = uiPalette.cardSurface),
                        border = BorderStroke(1.dp, uiPalette.cardBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.label_result),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                StatusPill(status = status, isBusy = isBusy)
                            }

                            Text(
                                text = if (resultText.isNotBlank()) resultText else stringResource(R.string.result_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (resultText.isNotBlank()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(uiPalette.resultSurface)
                                    .padding(16.dp),
                            )

                            Button(
                                onClick = {
                                    if (resultText.isBlank()) {
                                        status = StatusMessage(context.getString(R.string.error_copy_empty), Tone.ERROR)
                                    } else {
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText("AegisVault Result", resultText),
                                        )
                                        status = StatusMessage(
                                            context.getString(R.string.status_copied),
                                            Tone.SUCCESS,
                                        )
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = uiPalette.resultCopySurface,
                                    contentColor = uiPalette.resultCopyText,
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy_result),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.action_copy_result))
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = uiPalette.cardSurface),
                        border = BorderStroke(1.dp, uiPalette.cardBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.section_language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LanguageChip(
                                    selected = preferenceState.language == AppLanguage.ZH_CN,
                                    label = stringResource(R.string.language_zh),
                                    uiPalette = uiPalette,
                                    onClick = {
                                        if (preferenceState.language != AppLanguage.ZH_CN) {
                                            preferenceState = preferences.updateLanguage(AppLanguage.ZH_CN)
                                            activity.recreate()
                                        }
                                    },
                                )
                                LanguageChip(
                                    selected = preferenceState.language == AppLanguage.ENGLISH,
                                    label = stringResource(R.string.language_en),
                                    uiPalette = uiPalette,
                                    onClick = {
                                        if (preferenceState.language != AppLanguage.ENGLISH) {
                                            preferenceState = preferences.updateLanguage(AppLanguage.ENGLISH)
                                            activity.recreate()
                                        }
                                    },
                                )
                            }
                            Text(
                                text = stringResource(R.string.theme_follow_system_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(uiPalette: com.aegisvault.mobile.ui.theme.AegisVaultUiPalette) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            uiPalette.softAccentSurface,
                            uiPalette.cardSurface,
                            uiPalette.strongCardSurface,
                        ),
                        radius = 1200f,
                    ),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = uiPalette.heroText,
                )
                Text(
                    text = stringResource(R.string.hero_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = uiPalette.heroBody,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolModeSelector(
    selected: TextTool,
    uiPalette: com.aegisvault.mobile.ui.theme.AegisVaultUiPalette,
    onSelected: (TextTool) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onSelected(TextTool.AES) },
            selected = selected == TextTool.AES,
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = uiPalette.segmentedActiveSurface,
                activeContentColor = uiPalette.segmentedActiveText,
                inactiveContainerColor = uiPalette.segmentedInactiveSurface,
                inactiveContentColor = uiPalette.segmentedInactiveText,
            ),
            label = {
                Text(
                    text = stringResource(R.string.tool_aes),
                    fontWeight = FontWeight.Medium,
                )
            },
            modifier = Modifier.height(54.dp),
        )
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onSelected(TextTool.BASE64) },
            selected = selected == TextTool.BASE64,
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = uiPalette.segmentedActiveSurface,
                activeContentColor = uiPalette.segmentedActiveText,
                inactiveContainerColor = uiPalette.segmentedInactiveSurface,
                inactiveContentColor = uiPalette.segmentedInactiveText,
            ),
            label = {
                Text(
                    text = stringResource(R.string.tool_base64),
                    fontWeight = FontWeight.Medium,
                )
            },
            modifier = Modifier.height(54.dp),
        )
    }
}

@Composable
private fun SoftActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.55f),
            disabledContentColor = contentColor.copy(alpha = 0.65f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LanguageChip(
    selected: Boolean,
    label: String,
    uiPalette: com.aegisvault.mobile.ui.theme.AegisVaultUiPalette,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = uiPalette.chipSurface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = uiPalette.chipSelectedSurface,
            selectedLabelColor = uiPalette.chipSelectedText,
        ),
    )
}

@Composable
private fun StatusPill(status: StatusMessage, isBusy: Boolean) {
    val uiPalette = rememberAegisVaultUiPalette()
    val toneColor = when {
        isBusy || status.tone == Tone.PROCESSING -> MaterialTheme.colorScheme.secondary
        status.tone == Tone.SUCCESS -> uiPalette.successText
        status.tone == Tone.WARNING -> uiPalette.warningText
        status.tone == Tone.ERROR -> uiPalette.errorText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = when {
        isBusy || status.tone == Tone.PROCESSING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        status.tone == Tone.SUCCESS -> uiPalette.successSurface
        status.tone == Tone.WARNING -> uiPalette.warningSurface
        status.tone == Tone.ERROR -> uiPalette.errorSurface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Text(
        text = status.text,
        color = toneColor,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun errorMessage(context: Context, error: AegisVaultException): String {
    return when (error.code) {
        ErrorCode.EMPTY_TEXT -> context.getString(R.string.error_empty_text)
        ErrorCode.EMPTY_PASSWORD -> context.getString(R.string.error_empty_password)
        ErrorCode.AUTH -> context.getString(R.string.error_auth)
        ErrorCode.BASE64_INVALID -> context.getString(R.string.error_base64)
        ErrorCode.PROTOCOL -> context.getString(R.string.error_protocol)
    }
}
