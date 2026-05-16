package com.aegisvault.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aegisvault.mobile.data.ThemeOption

@Composable
fun AegisVaultTheme(themeOption: ThemeOption, content: @Composable () -> Unit) {
    val dark = when (themeOption) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.DARK -> true
        ThemeOption.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeOption == ThemeOption.SYSTEM) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
