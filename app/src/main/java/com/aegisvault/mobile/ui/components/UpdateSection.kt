package com.aegisvault.mobile.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aegisvault.mobile.BuildConfig
import com.aegisvault.mobile.R
import com.aegisvault.mobile.update.*
import kotlinx.coroutines.launch

@Composable
fun UpdateSection(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember(context) { AndroidUpdateFiles(context, GitHubHttpsTransport(BuildConfig.VERSION_NAME)) }
    var installing by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<Int?>(null) }
    val busy = state.phase == UpdatePhase.CHECKING || state.phase == UpdatePhase.DOWNLOADING
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        Text(stringResource(R.string.update_privacy), style = MaterialTheme.typography.bodySmall)
        val offer = state.check?.offer
        when (state.phase) {
            UpdatePhase.CHECKING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.update_checking)) }
            UpdatePhase.CURRENT -> Text(stringResource(if (state.check?.ahead == true) R.string.update_ahead else R.string.update_latest,
                state.check?.latest.orEmpty()))
            UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY -> offer?.let {
                Text(stringResource(R.string.update_available, it.versionName, it.versionCode, it.apk.size / 1024 / 1024))
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    val fraction = (state.downloaded.toFloat() / it.apk.size).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_download_progress, (fraction * 100).toInt()))
                }
                if (state.phase == UpdatePhase.READY) Text(stringResource(R.string.update_verified))
            }
            UpdatePhase.ERROR -> Text(stringResource(updateErrorResource(state.error)))
            UpdatePhase.IDLE -> Unit
        }
        installMessage?.let { Text(stringResource(it)) }
        if (busy) OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.update_cancel)) }
        else OutlinedButton(onClick = { installMessage = null; vm.check() }, enabled = !installing) { Text(stringResource(R.string.update_check)) }
        if (offer != null && state.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR)) {
            Button(onClick = { installMessage = null; vm.download() }) { Text(stringResource(R.string.update_download)) }
        }
        if (offer != null && state.phase == UpdatePhase.READY) {
            Button(enabled = !installing, onClick = {
                if (installing) return@Button
                installing = true
                scope.launch {
                    try {
                        val verified = files.verifyReady(offer)
                        if (files.needsInstallPermission()) {
                            context.startActivity(files.permissionIntent())
                            installMessage = R.string.update_allow_install
                        } else {
                            context.startActivity(files.installIntent(verified))
                            installMessage = R.string.update_install_started
                        }
                    } catch (error: UpdateException) { installMessage = updateErrorResource(error.error) }
                    catch (_: ActivityNotFoundException) { installMessage = R.string.update_no_installer }
                    catch (_: SecurityException) { installMessage = R.string.update_allow_install }
                    finally { installing = false }
                }
            }) { Text(stringResource(R.string.update_install)) }
        }
        TextButton(onClick = {
            val page = offer?.releasePage ?: state.check?.let { "${UpdateContract.REPOSITORY}/releases/tag/v${it.latest}" }
                ?: "${UpdateContract.REPOSITORY}/releases/latest"
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }
            catch (_: ActivityNotFoundException) { installMessage = R.string.update_no_browser }
        }, enabled = !installing) { Text(stringResource(R.string.update_release_page)) }
    }
}

private fun updateErrorResource(error: UpdateError?): Int = when (error) {
    UpdateError.RATE_LIMIT -> R.string.update_rate_limit
    UpdateError.INVALID_RELEASE, UpdateError.UNSUPPORTED_MANIFEST -> R.string.update_invalid
    UpdateError.INCOMPATIBLE -> R.string.update_incompatible
    UpdateError.INTEGRITY, UpdateError.PACKAGE_MISMATCH -> R.string.update_integrity
    UpdateError.STORAGE -> R.string.update_storage
    UpdateError.NO_INSTALLER -> R.string.update_no_installer
    else -> R.string.update_network
}
