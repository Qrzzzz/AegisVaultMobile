package com.aegisvault.mobile

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aegisvault.mobile.data.AppPreferencesRepository
import com.aegisvault.mobile.ui.AegisVaultRoute
import com.aegisvault.mobile.ui.AegisVaultViewModel
import com.aegisvault.mobile.ui.theme.AegisVaultTheme

@Composable
fun AegisVaultApp(activity: Activity) {
    val context = LocalContext.current
    val vm: AegisVaultViewModel = viewModel(factory = AegisVaultViewModel.Factory(AppPreferencesRepository(context)))
    val state = vm.uiState.collectAsStateWithLifecycle().value
    DisposableEffect(state.settings.protectScreen) {
        if (state.settings.protectScreen) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
    AegisVaultTheme(themeOption = state.settings.theme) { AegisVaultRoute(vm) }
}
